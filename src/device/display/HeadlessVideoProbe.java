package device.display;

import core.emulator.HardwareManager;
import core.exception.HardwareException;
import core.memory.memory8.Memory8;
import core.memory.memory8.MemoryBusIIe;
import device.display.display8.ScanlineTracer8;

/**
 * Headless monitor timing/probe used to supply floating-bus style reads
 * without creating a UI window.
 */
public class HeadlessVideoProbe extends HardwareManager implements VideoSignalSource {

	private final MemoryBusIIe memoryBus;
	private final Memory8 memory;
	private final ScanlineTracer8 tracer;
	private final int resetPhaseCycles;
	private int lastSwitchIteration;

	public HeadlessVideoProbe(MemoryBusIIe memoryBus, long unitsPerCycle) {
		super(unitsPerCycle);
		this.memoryBus = memoryBus;
		this.memory = memoryBus.getMemory();
		this.tracer = new ScanlineTracer8();
		this.resetPhaseCycles = Integer.getInteger("ever2e.headless.vbl.phaseCycles", 0);
		tracer.setScanStart(25, 70);
		tracer.setScanSize(65, 262);
		coldResetNoThrow();
	}

	private void coldResetNoThrow() {
		lastSwitchIteration = -1;
		evaluateSwitchChange();
		tracer.coldReset();
		if( resetPhaseCycles>0 )
			advanceCycles(resetPhaseCycles);
	}

	private void evaluateSwitchChange() {
		tracer.setPage(memoryBus.isPage2() && !memoryBus.is80Store() ? 2 : 1);
		if( memoryBus.isHiRes() )
			tracer.setTraceMap(DisplayIIe.HI40_TRACE);
		else
			tracer.setTraceMap(DisplayIIe.LO40_TRACE);
	}

	@Override
	public void cycle() throws HardwareException {
		incSleepCycles(1);
		if( lastSwitchIteration!=memoryBus.getSwitchIteration() ) {
			lastSwitchIteration = memoryBus.getSwitchIteration();
			evaluateSwitchChange();
		}
		tracer.cycle();
	}

	public void advanceCycles(int cycles) {
		for( int i = 0; i<cycles; i++ ) {
			try {
				cycle();
			} catch( HardwareException e ) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void coldReset() throws HardwareException {
		coldResetNoThrow();
	}

	@Override
	public int getLastRead() {
		// Keep soft-switch display mode/page changes visible to floating-bus reads
		// immediately, even between probe cycles.
		if( lastSwitchIteration!=memoryBus.getSwitchIteration() ) {
			lastSwitchIteration = memoryBus.getSwitchIteration();
			evaluateSwitchChange();
		}
		return memory.getByte(tracer.getAddress());
	}

	@Override
	public int getHScan() {
		return tracer.getHScan();
	}

	@Override
	public boolean isVbl() {
		return tracer.isVbl();
	}

	@Override
	public int getVScan() {
		return tracer.getVScan();
	}
}
