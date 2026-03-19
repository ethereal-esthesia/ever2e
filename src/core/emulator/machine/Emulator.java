package core.emulator.machine;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import core.exception.HardwareException;
import core.emulator.HardwareManager;

public class Emulator {
	private static volatile boolean blockingDebugEnabled;
	private static final long STEP_LISTENER_DEBUG_THRESHOLD_NS = 2_000_000L; // 2ms
	private static final long MANAGER_CYCLE_DEBUG_THRESHOLD_NS = 20_000_000L; // 20ms
	private static final long SPEAKER_MANAGER_CYCLE_DEBUG_THRESHOLD_NS = 200_000_000L; // 200ms
	private static final long SLEEP_OVERSHOOT_DEBUG_THRESHOLD_NS = 20_000_000L; // 20ms
	private static final long HANG_WATCHDOG_WARN_NS = 2_000_000_000L; // 2s
	private static final long HANG_WATCHDOG_POLL_MS = 250L;
	private static final long MAX_SLEEP_CHUNK_MS = 50L;
	private static final long ABNORMAL_SLEEP_REQUEST_MS = 500L;
	private static final int HANG_STACK_TRACE_FRAMES = 8;

	public static interface StepListener {
		void onStep( long step, HardwareManager manager );
	}
	public static interface StepPhaseListener {
		boolean onStepPhase( long step, HardwareManager manager, boolean preCycle );
	}

	protected PriorityQueue<HardwareManager> hardwareManagerQueue;
	protected int granularityBitsPerSecond;
	private boolean realtimeThrottleEnabled = true;
	
	public Emulator(PriorityQueue<HardwareManager> hardwareManagerQueue, int granularityBitsPerMs)
			throws HardwareException {
		this.hardwareManagerQueue = hardwareManagerQueue;
		this.granularityBitsPerSecond = granularityBitsPerMs;
		coldReset();
	}

	public static void setBlockingDebugEnabled(boolean enabled) {
		blockingDebugEnabled = enabled;
	}

	public void setRealtimeThrottleEnabled(boolean enabled) {
		realtimeThrottleEnabled = enabled;
	}

	public void start() throws HardwareException, InterruptedException {
		start(-1, null, null);
	}

	public long start( long maxSteps, HardwareManager stepManager ) throws HardwareException, InterruptedException {
		return start(maxSteps, stepManager, null);
	}

	public long start( long maxSteps, HardwareManager stepManager, StepListener stepListener ) throws HardwareException, InterruptedException {
		return startWithStepPhases(maxSteps, stepManager, stepListener==null ? null : (step, manager, preCycle) -> {
			if( !preCycle )
				stepListener.onStep(step, manager);
			return true;
		});
	}

	public long startWithStepPhases( long maxSteps, HardwareManager stepManager, StepPhaseListener stepPhaseListener ) throws HardwareException, InterruptedException {
		long schedulerStartNs = System.nanoTime();
		long steps = 0;
		final AtomicLong lastProgressNs = new AtomicLong(System.nanoTime());
		final AtomicLong opStartNs = new AtomicLong(0L);
		final AtomicLong lastHangReportNs = new AtomicLong(0L);
		final AtomicReference<String> currentOperation = new AtomicReference<>("init");
		final Thread emulatorThread = Thread.currentThread();
		Thread hangWatchdog = null;
		if( blockingDebugEnabled ) {
			hangWatchdog = new Thread(() -> {
				while( !Thread.currentThread().isInterrupted() ) {
					long now = System.nanoTime();
					long progressAgeNs = now - lastProgressNs.get();
					String op = currentOperation.get();
					if( progressAgeNs>=HANG_WATCHDOG_WARN_NS && !op.startsWith("scheduler_sleep(") ) {
						long startedAt = opStartNs.get();
						long opAgeNs = startedAt==0L ? progressAgeNs : (now-startedAt);
						long previousReport = lastHangReportNs.get();
						if( now-previousReport>=HANG_WATCHDOG_WARN_NS && lastHangReportNs.compareAndSet(previousReport, now) ) {
							System.out.println("[debug] potential_hang op="+op+
									" stuckMs="+(opAgeNs/1_000_000.0)+
									" thread="+emulatorThread.getName()+
									" state="+emulatorThread.getState());
							StackTraceElement[] stack = emulatorThread.getStackTrace();
							int maxFrames = Math.min(HANG_STACK_TRACE_FRAMES, stack.length);
							for( int i = 0; i<maxFrames; i++ ) {
								System.out.println("[debug] stack["+i+"] "+stack[i]);
							}
						}
					}
					try {
						Thread.sleep(HANG_WATCHDOG_POLL_MS);
					}
					catch( InterruptedException e ) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}, "EmulatorBlockingWatchdog");
			hangWatchdog.setDaemon(true);
			hangWatchdog.start();
		}
		try {
		do {
			HardwareManager nextManager = hardwareManagerQueue.remove();
			long clockTime = (System.nanoTime()-schedulerStartNs)/1_000_000L;
			if( realtimeThrottleEnabled && maxSteps<0 ) {
				long waitTime = (nextManager.getNextCycleUnits()>>granularityBitsPerSecond)-clockTime;
				if( waitTime>0 ) {
					if( blockingDebugEnabled ) {
						currentOperation.set("scheduler_sleep("+nextManager.getClass().getSimpleName()+")");
						opStartNs.set(System.nanoTime());
						if( waitTime>=ABNORMAL_SLEEP_REQUEST_MS ) {
							System.out.println("[debug] scheduler_sleep_request_abnormal manager="+nextManager.getClass().getSimpleName()+
									" requestedMs="+waitTime+
									" nextCycleMs="+(nextManager.getNextCycleUnits()>>granularityBitsPerSecond)+
									" clockMs="+clockTime);
						}
					}
					long remainingSleepMs = waitTime;
					while( remainingSleepMs>0 ) {
						long chunkMs = Math.min(remainingSleepMs, MAX_SLEEP_CHUNK_MS);
						if( blockingDebugEnabled ) {
							long startNs = System.nanoTime();
							Thread.sleep(chunkMs);
							long elapsedNs = System.nanoTime()-startNs;
							long requestedNs = chunkMs * 1_000_000L;
							long overshootNs = elapsedNs-requestedNs;
							if( overshootNs>=SLEEP_OVERSHOOT_DEBUG_THRESHOLD_NS ) {
								System.out.println("[debug] scheduler_sleep_overshoot manager="+nextManager.getClass().getSimpleName()+
										" requestedMs="+chunkMs+
										" elapsedMs="+(elapsedNs/1_000_000.0)+
										" overshootMs="+(overshootNs/1_000_000.0));
							}
						}
						else {
							Thread.sleep(chunkMs);
						}
						remainingSleepMs -= chunkMs;
					}
				}
			}
			if( maxSteps>=0 && nextManager==stepManager ) {
				long stepNumber = steps+1;
				boolean continueRun = true;
				if( stepPhaseListener!=null ) {
					if( blockingDebugEnabled ) {
						currentOperation.set("step_listener_pre("+nextManager.getClass().getSimpleName()+")");
						opStartNs.set(System.nanoTime());
						long startNs = System.nanoTime();
						continueRun = stepPhaseListener.onStepPhase(stepNumber, nextManager, true);
						long elapsedNs = System.nanoTime()-startNs;
						if( elapsedNs>=STEP_LISTENER_DEBUG_THRESHOLD_NS ) {
							System.out.println("[debug] step_listener_blocked phase=pre step="+stepNumber+
									" manager="+nextManager.getClass().getSimpleName()+
									" elapsedMs="+(elapsedNs/1_000_000.0));
						}
					}
					else
						continueRun = stepPhaseListener.onStepPhase(stepNumber, nextManager, true);
				}
				if( !continueRun ) {
					hardwareManagerQueue.add(nextManager);
					break;
				}
				steps = stepNumber;
			}
			if( blockingDebugEnabled ) {
				currentOperation.set("manager_cycle("+nextManager.getClass().getSimpleName()+")");
				opStartNs.set(System.nanoTime());
				long startNs = System.nanoTime();
				nextManager.cycle();
				long elapsedNs = System.nanoTime()-startNs;
				long cycleThresholdNs = getManagerCycleDebugThresholdNs(nextManager);
				if( elapsedNs>=cycleThresholdNs ) {
					System.out.println("[debug] manager_cycle_blocked manager="+nextManager.getClass().getSimpleName()+
							" elapsedMs="+(elapsedNs/1_000_000.0));
				}
			}
			else {
				nextManager.cycle();
			}
			if( maxSteps>=0 && nextManager==stepManager ) {
				boolean continueRun = true;
				if( stepPhaseListener!=null ) {
					if( blockingDebugEnabled ) {
						currentOperation.set("step_listener_post("+nextManager.getClass().getSimpleName()+")");
						opStartNs.set(System.nanoTime());
						long startNs = System.nanoTime();
						continueRun = stepPhaseListener.onStepPhase(steps, nextManager, false);
						long elapsedNs = System.nanoTime()-startNs;
						if( elapsedNs>=STEP_LISTENER_DEBUG_THRESHOLD_NS ) {
							System.out.println("[debug] step_listener_blocked phase=post step="+steps+
									" manager="+nextManager.getClass().getSimpleName()+
									" elapsedMs="+(elapsedNs/1_000_000.0));
						}
					}
					else
						continueRun = stepPhaseListener.onStepPhase(steps, nextManager, false);
				}
				if( !continueRun ) {
					hardwareManagerQueue.add(nextManager);
					break;
				}
				if( steps>=maxSteps ) {
					hardwareManagerQueue.add(nextManager);
					break;
				}
			}
			hardwareManagerQueue.add(nextManager);
			if( blockingDebugEnabled ) {
				long now = System.nanoTime();
				lastProgressNs.set(now);
				currentOperation.set("idle");
				opStartNs.set(now);
			}
		} while(true);
		}
		finally {
			if( hangWatchdog!=null )
				hangWatchdog.interrupt();
		}
		return steps;
	}

	private static long getManagerCycleDebugThresholdNs(HardwareManager manager) {
		String managerName = manager.getClass().getSimpleName();
		if( "Speaker1Bit".equals(managerName) )
			return SPEAKER_MANAGER_CYCLE_DEBUG_THRESHOLD_NS;
		return MANAGER_CYCLE_DEBUG_THRESHOLD_NS;
	}

	public void coldReset() throws HardwareException {
		HardwareManager[] managerList = new HardwareManager[hardwareManagerQueue.size()];
		for( HardwareManager manager : hardwareManagerQueue.toArray(managerList) )
			manager.coldReset();
	}

}
