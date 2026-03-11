package core.emulator.machine.machine8;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;


import peripherals.PeripheralIIe;
import core.cpu.cpu8.Cpu65c02;
import core.cpu.cpu8.Cpu65c02Cmd;
import core.cpu.cpu8.Opcode;
import core.emulator.HardwareManager;
import core.emulator.VirtualMachineProperties;
import core.emulator.VirtualMachineProperties.MachineLayoutType;
import core.emulator.machine.Emulator;
import core.exception.HardwareException;
import core.memory.memory8.Memory8;
import core.memory.memory8.MemoryBus8;
import core.memory.memory8.MemoryBusDemo8;
import core.memory.memory8.MemoryBusIIe;
import device.display.Display32x32;
import device.display.Display32x32Console;
import device.display.DisplayConsoleAppleIIe;
import device.display.DisplayConsoleDebug;
import device.display.DisplayIIe;
import device.display.HeadlessVideoProbe;
import device.display.VideoSignalSource;
import device.keyboard.KeyboardIIe;
import device.speaker.Speaker1Bit;

public class Emulator8Coordinator {

	private static final String DEFAULT_MACHINE = "ROMS/Apple2e.emu";
	private static final int GRANULARITY_BITS_PER_MS = 32;
	private static final boolean ENABLE_STARTUP_JIT_PRIME = true;
	private static final int STARTUP_JIT_PRIME_STEPS = 300000;
	private static final long MONITOR_BLOCKING_DEBUG_THRESHOLD_NS = 2_000_000L; // 2ms
	private static final int DISPLAY_HSCAN_CYCLES = 65;
	private static final int DISPLAY_VSCAN_LINES = 262;
	private static final int DISPLAY_VBL_LINES = 70;

	private static int parseByteArg(String value, String argName) {
		String raw = value.trim();
		int parsed;
		if( raw.toLowerCase().startsWith("0x") ) {
			parsed = Integer.parseInt(raw.substring(2), 16);
		}
		else if( raw.matches("^[0-9A-Fa-f]{1,2}$") && raw.matches(".*[A-Fa-f].*") ) {
			parsed = Integer.parseInt(raw, 16);
		}
		else {
			parsed = Integer.parseInt(raw, 10);
		}
		if( parsed<0 || parsed>0xff )
			throw new IllegalArgumentException(argName+" must be in [0..255], got "+value);
		return parsed;
	}

	private static int parseWordArg(String value, String argName) {
		String raw = value.trim();
		int parsed;
		if( raw.toLowerCase().startsWith("0x") ) {
			parsed = Integer.parseInt(raw.substring(2), 16);
		}
		else if( raw.matches("^[0-9A-Fa-f]{1,4}$") && raw.matches(".*[A-Fa-f].*") ) {
			parsed = Integer.parseInt(raw, 16);
		}
		else {
			parsed = Integer.parseInt(raw, 10);
		}
		if( parsed<0 || parsed>0xffff )
			throw new IllegalArgumentException(argName+" must be in [0..65535], got "+value);
		return parsed;
	}

	private static void parseWordListArg(String value, String argName, Set<Integer> out) {
		String raw = value==null ? "" : value.trim();
		if( raw.isEmpty() )
			throw new IllegalArgumentException("Missing value for "+argName);
		for( String token : raw.split(",") ) {
			String t = token.trim();
			if( t.isEmpty() )
				continue;
			out.add(parseWordArg(t, argName));
		}
		if( out.isEmpty() )
			throw new IllegalArgumentException("Missing value for "+argName);
	}

	private static void parseMonitorSequenceWriteArg(String value, String argName, List<int[]> out) {
		String raw = value==null ? "" : value.trim();
		if( raw.isEmpty() )
			throw new IllegalArgumentException("Missing value for "+argName);
		for( String token : raw.split(",") ) {
			String t = token.trim();
			if( t.isEmpty() )
				continue;
			String[] parts = t.split(":");
			if( parts.length!=2 )
				throw new IllegalArgumentException(argName+" expects ADDR:BYTE pairs, got '"+t+"'");
			int addr = parseWordArg(parts[0].trim(), argName);
			int val = parseByteArg(parts[1].trim(), argName);
			out.add(new int[] { addr&0xffff, val&0xff });
		}
		if( out.isEmpty() )
			throw new IllegalArgumentException("Missing value for "+argName);
	}

	private static String formatMonitorSequenceWrites(List<int[]> writes) {
		if( writes==null || writes.isEmpty() )
			return "";
		StringBuilder out = new StringBuilder();
		for( int i = 0; i<writes.size(); i++ ) {
			if( i>0 )
				out.append(", ");
			int[] pair = writes.get(i);
			out.append(Cpu65c02.getHexString(pair[0], 4)).append(":").append(Cpu65c02.getHexString(pair[1], 2));
		}
		return out.toString();
	}

	private static int[] parseWordRangeArg(String value, String argName) {
		String raw = value==null ? "" : value.trim();
		if( raw.isEmpty() )
			throw new IllegalArgumentException("Missing value for "+argName);
		String[] parts = raw.split(":", -1);
		if( parts.length!=2 )
			throw new IllegalArgumentException(argName+" must be in form <start:end>, got "+value);
		int start = parseWordArg(parts[0].trim(), argName);
		int end = parseWordArg(parts[1].trim(), argName);
		if( end<start )
			throw new IllegalArgumentException(argName+" end must be >= start, got "+value);
		return new int[] { start, end };
	}

	private static void queueBasicText(KeyboardIIe keyboard, String source, String basicText) {
		// Startup script injection should not lock out immediate live typing.
		keyboard.queuePasteText(basicText, false);
		System.out.println("Queued BASIC paste from "+source+" ("+basicText.length()+" chars)");
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static void runSilently(ThrowingRunnable action) throws Exception {
		PrintStream originalOut = System.out;
		PrintStream silentOut = new PrintStream(OutputStream.nullOutputStream());
		try {
			System.setOut(silentOut);
			action.run();
		}
		finally {
			System.setOut(originalOut);
			silentOut.close();
		}
	}

	private static void maybeLogMonitorAdvanceBlocking(boolean debugLogging, long elapsedNs, int cycles) {
		if( !debugLogging || elapsedNs<MONITOR_BLOCKING_DEBUG_THRESHOLD_NS )
			return;
		System.out.println("[debug] monitor_advance_blocked cycles="+cycles+
				" elapsedMs="+(elapsedNs/1_000_000.0));
	}

	private static void loadProgramImage(VirtualMachineProperties properties, Memory8 memory, MemoryBus8 bus, byte[] rom16k) {
		Arrays.fill(rom16k, (byte) 0);
		byte [] program = properties.getCode();

		// Set up initial ROM or RAM program
		int addr = properties.getProgramStart();
		for( byte opcode : program ) {
			if( bus.getClass()==MemoryBusIIe.class && addr>=MemoryBusIIe.ROM_START )
				rom16k[addr-MemoryBusIIe.ROM_START] = opcode;
			else
				memory.setByte(addr, opcode);
			addr++;
		}

		// Set program start
		if( properties.getCode().length+properties.getProgramStart()<0xfffd ) {
			if( bus.getClass()==MemoryBusIIe.class ) {
				rom16k[0xfffc-MemoryBusIIe.ROM_START] = (byte) properties.getProgramStart();
				rom16k[0xfffd-MemoryBusIIe.ROM_START] = (byte) (properties.getProgramStart()>>8);
			} else {
				memory.setByte(0xfffc, properties.getProgramStart());
				memory.setByte(0xfffd, properties.getProgramStart()>>8);
			}
		}
	}

	private static Cpu65c02 createCpu(String cpuProfile, MemoryBusIIe bus, long unitsPerCycle) {
		if( "cmd".equals(cpuProfile) )
			return new Cpu65c02Cmd(bus, unitsPerCycle);
		return new Cpu65c02(bus, unitsPerCycle); // wdc
	}

	public static void main( String[] argList ) throws HardwareException, InterruptedException, IOException {

		String propertiesFile = DEFAULT_MACHINE;
		long maxCpuSteps = -1;
		String traceFile = null;
		String tracePhase = "pre";
		boolean traceSubinstructions = false;
		boolean traceKeyValue = false;
		Integer traceStartPc = null;
			boolean textConsole = false;
			boolean printTextAtExit = false;
			String lastFrameOut = null;
			boolean printCpuStateAtExit = false;
			boolean showFps = false;
			boolean noSound = false;
			boolean debugLogging = false;
			boolean keyLogging = false;
			boolean mouseDebug = false;
		boolean startFullscreen = false;
		boolean macAllowProcessSwitching = false;
		String textInputMode = "off";
		String sdlFullscreenMode = "exclusive";
		boolean sdlImeUiSelf = false;
		String cpuProfile = "cmd";
		Integer resetPFlagValue = null;
		Integer resetAValue = null;
		Integer resetXValue = null;
		Integer resetYValue = null;
		Integer resetSValue = null;
		boolean floatingBusOpcodeTiming = false;
		Integer displayPhaseCycles = null;
		long startupCpuManagerCalls = 0L;
		long startupDisplayCycles = 0L;
		Integer dumpPageAddress = null;
		int dumpRangeStart = -1;
		int dumpRangeEnd = -1;
		boolean dumpAllMapped = false;
		boolean dumpAllRawRam = false;
		List<int[]> monitorSequenceWrites = new ArrayList<>();
		Set<Integer> haltExecutions = new LinkedHashSet<>();
		Set<Integer> requireHaltPcs = new LinkedHashSet<>();
			String pasteFile = null;
		String pasteText = null;
		for( int i = 0; i<argList.length; i++ ) {
			String arg = argList[i];
			if( "--steps".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --steps");
				maxCpuSteps = Long.parseLong(argList[++i]);
			}
			else if( arg.startsWith("--steps=") ) {
				maxCpuSteps = Long.parseLong(arg.substring("--steps=".length()));
			}
			else if( "--trace-file".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --trace-file");
				traceFile = argList[++i];
			}
			else if( arg.startsWith("--trace-file=") ) {
				traceFile = arg.substring("--trace-file=".length());
			}
			else if( "--trace-phase".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --trace-phase");
				tracePhase = argList[++i];
			}
			else if( arg.startsWith("--trace-phase=") ) {
				tracePhase = arg.substring("--trace-phase=".length());
			}
			else if( "--post".equals(arg) ) {
				tracePhase = "post";
			}
			else if( "--trace-subinstructions".equals(arg) ) {
				traceSubinstructions = true;
			}
			else if( "--trace-microcode".equals(arg) ) {
				traceSubinstructions = true;
			}
			else if( "--cpu-profile".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --cpu-profile");
				cpuProfile = argList[++i].trim().toLowerCase();
			}
			else if( arg.startsWith("--cpu-profile=") ) {
				cpuProfile = arg.substring("--cpu-profile=".length()).trim().toLowerCase();
			}
			else if( "--trace-kv".equals(arg) ) {
				traceKeyValue = true;
			}
			else if( "--text-console".equals(arg) ) {
				textConsole = true;
			}
			else if( "--print-text-at-exit".equals(arg) ) {
				printTextAtExit = true;
			}
			else if( "--last-frame-out".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --last-frame-out");
				lastFrameOut = argList[++i];
			}
			else if( arg.startsWith("--last-frame-out=") ) {
				lastFrameOut = arg.substring("--last-frame-out=".length());
			}
			else if( "--print-cpu-state-at-exit".equals(arg) ) {
				printCpuStateAtExit = true;
			}
			else if( "--show-fps".equals(arg) ) {
				showFps = true;
			}
			else if( "--no-sound".equals(arg) ) {
				noSound = true;
			}
			else if( "--no-logging".equals(arg) ) {
				debugLogging = false;
			}
			else if( "--debug".equals(arg) ) {
				debugLogging = true;
			}
			else if( "--keylog".equals(arg) || "--key-log".equals(arg) ) {
				keyLogging = true;
			}
			else if( arg.startsWith("--key-log=") ) {
				keyLogging = Boolean.parseBoolean(arg.substring("--key-log=".length()));
			}
			else if( "--debug-mouse".equals(arg) ) {
				mouseDebug = true;
			}
			else if( "--start-fullscreen".equals(arg) ) {
				startFullscreen = true;
			}
			else if( "--mac-allow-process-switching".equals(arg) ) {
				macAllowProcessSwitching = true;
			}
			else if( "--mac-disable-process-switching".equals(arg) ) {
				// Backward-compatible alias: lock is now the default in fullscreen.
				macAllowProcessSwitching = false;
			}
			else if( "--mac-kiosk-grab".equals(arg) ) {
				// Backward-compatible alias: lock is now the default in fullscreen.
				macAllowProcessSwitching = false;
			}
			else if( "--text-input-mode".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --text-input-mode");
				textInputMode = argList[++i];
			}
			else if( arg.startsWith("--text-input-mode=") ) {
				textInputMode = arg.substring("--text-input-mode=".length());
			}
			else if( "--sdl-fullscreen-mode".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --sdl-fullscreen-mode");
				sdlFullscreenMode = argList[++i];
			}
			else if( arg.startsWith("--sdl-fullscreen-mode=") ) {
				sdlFullscreenMode = arg.substring("--sdl-fullscreen-mode=".length());
			}
			else if( "--sdl-ime-ui-self".equals(arg) ) {
				sdlImeUiSelf = true;
			}
			else if( "--trace-start-pc".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --trace-start-pc");
				traceStartPc = parseWordArg(argList[++i], "--trace-start-pc");
			}
			else if( arg.startsWith("--trace-start-pc=") ) {
				traceStartPc = parseWordArg(arg.substring("--trace-start-pc=".length()), "--trace-start-pc");
			}
			else if( "--reset-pflag-value".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --reset-pflag-value");
				resetPFlagValue = parseByteArg(argList[++i], "--reset-pflag-value") | 0x30;
			}
			else if( arg.startsWith("--reset-pflag-value=") ) {
				resetPFlagValue = parseByteArg(arg.substring("--reset-pflag-value=".length()), "--reset-pflag-value") | 0x30;
			}
			else if( "--reset-a-value".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --reset-a-value");
				resetAValue = parseByteArg(argList[++i], "--reset-a-value");
			}
			else if( arg.startsWith("--reset-a-value=") ) {
				resetAValue = parseByteArg(arg.substring("--reset-a-value=".length()), "--reset-a-value");
			}
			else if( "--reset-x-value".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --reset-x-value");
				resetXValue = parseByteArg(argList[++i], "--reset-x-value");
			}
			else if( arg.startsWith("--reset-x-value=") ) {
				resetXValue = parseByteArg(arg.substring("--reset-x-value=".length()), "--reset-x-value");
			}
			else if( "--reset-y-value".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --reset-y-value");
				resetYValue = parseByteArg(argList[++i], "--reset-y-value");
			}
			else if( arg.startsWith("--reset-y-value=") ) {
				resetYValue = parseByteArg(arg.substring("--reset-y-value=".length()), "--reset-y-value");
			}
			else if( "--reset-s-value".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --reset-s-value");
				resetSValue = parseByteArg(argList[++i], "--reset-s-value");
			}
			else if( arg.startsWith("--reset-s-value=") ) {
				resetSValue = parseByteArg(arg.substring("--reset-s-value=".length()), "--reset-s-value");
			}
			else if( "--floating-bus-opcode-timing".equals(arg) ) {
				floatingBusOpcodeTiming = true;
			}
			else if( "--display-phase-cycles".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --display-phase-cycles");
				displayPhaseCycles = Integer.parseInt(argList[++i]);
			}
			else if( arg.startsWith("--display-phase-cycles=") ) {
				displayPhaseCycles = Integer.parseInt(arg.substring("--display-phase-cycles=".length()));
			}
			else if( "--startup-cpu-manager-calls".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --startup-cpu-manager-calls");
				startupCpuManagerCalls = Long.parseLong(argList[++i]);
			}
			else if( arg.startsWith("--startup-cpu-manager-calls=") ) {
				startupCpuManagerCalls = Long.parseLong(arg.substring("--startup-cpu-manager-calls=".length()));
			}
			else if( "--startup-display-cycles".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --startup-display-cycles");
				startupDisplayCycles = Long.parseLong(argList[++i]);
			}
			else if( arg.startsWith("--startup-display-cycles=") ) {
				startupDisplayCycles = Long.parseLong(arg.substring("--startup-display-cycles=".length()));
			}
			else if( "--dump-page".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --dump-page");
				dumpPageAddress = parseWordArg(argList[++i], "--dump-page");
			}
			else if( arg.startsWith("--dump-page=") ) {
				dumpPageAddress = parseWordArg(arg.substring("--dump-page=".length()), "--dump-page");
			}
			else if( "--dump-range".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --dump-range");
				int[] range = parseWordRangeArg(argList[++i], "--dump-range");
				dumpRangeStart = range[0];
				dumpRangeEnd = range[1];
			}
			else if( arg.startsWith("--dump-range=") ) {
				int[] range = parseWordRangeArg(arg.substring("--dump-range=".length()), "--dump-range");
				dumpRangeStart = range[0];
				dumpRangeEnd = range[1];
			}
			else if( "--dump-mapped".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --dump-mapped");
				int[] range = parseWordRangeArg(argList[++i], "--dump-mapped");
				dumpRangeStart = range[0];
				dumpRangeEnd = range[1];
			}
			else if( arg.startsWith("--dump-mapped=") ) {
				int[] range = parseWordRangeArg(arg.substring("--dump-mapped=".length()), "--dump-mapped");
				dumpRangeStart = range[0];
				dumpRangeEnd = range[1];
			}
			else if( "--dump-all".equals(arg) ) {
				// Backward-compatible alias for full mapped 64K dump.
				dumpAllMapped = true;
			}
			else if( "--dump-all-64k".equals(arg) ) {
				dumpAllMapped = true;
			}
			else if( "--dump-64k".equals(arg) ) {
				// Backward-compatible alias for --dump-all-64k.
				dumpAllMapped = true;
			}
			else if( "--dump-all-mapped".equals(arg) ) {
				// Backward-compatible alias for full mapped 64K dump.
				dumpAllMapped = true;
			}
			else if( "--dump-all-128k".equals(arg) ) {
				// Backward-compatible alias for raw full-memory dump.
				dumpAllRawRam = true;
			}
			else if( "--dump-unmapped".equals(arg) ) {
				dumpAllRawRam = true;
			}
			else if( "--dump-all-raw-ram".equals(arg) ) {
				// Backward-compatible alias for raw full-memory dump.
				dumpAllRawRam = true;
			}
			else if( "--dump-all-raw-ram-rom".equals(arg) ) {
				dumpAllRawRam = true;
			}
			else if( "--monitor-seq-write".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --monitor-seq-write");
				parseMonitorSequenceWriteArg(argList[++i], "--monitor-seq-write", monitorSequenceWrites);
			}
			else if( arg.startsWith("--monitor-seq-write=") ) {
				parseMonitorSequenceWriteArg(arg.substring("--monitor-seq-write=".length()), "--monitor-seq-write", monitorSequenceWrites);
			}
			else if( "--halt-execution".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --halt-execution");
				parseWordListArg(argList[++i], "--halt-execution", haltExecutions);
			}
			else if( arg.startsWith("--halt-execution=") ) {
				parseWordListArg(arg.substring("--halt-execution=".length()), "--halt-execution", haltExecutions);
			}
			else if( "--require-halt-pc".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --require-halt-pc");
				parseWordListArg(argList[++i], "--require-halt-pc", requireHaltPcs);
			}
			else if( arg.startsWith("--require-halt-pc=") ) {
				parseWordListArg(arg.substring("--require-halt-pc=".length()), "--require-halt-pc", requireHaltPcs);
			}
			else if( "--paste-file".equals(arg) ) {
				if( i+1>=argList.length )
					throw new IllegalArgumentException("Missing value for --paste-file");
				pasteFile = argList[++i];
			}
			else if( arg.startsWith("--paste-file=") ) {
				pasteFile = arg.substring("--paste-file=".length());
			}
			else {
				if( arg.startsWith("-") )
					throw new IllegalArgumentException("Unknown option: "+arg);
				propertiesFile = arg;
			}
		}
		Emulator.setBlockingDebugEnabled(debugLogging);
		Speaker1Bit.setBlockingDebugEnabled(debugLogging);
		KeyboardIIe.setKeyLoggingEnabled(keyLogging);
		DisplayIIe.setKeyLoggingEnabled(keyLogging);
		DisplayIIe.setStartFullscreenOnLaunch(startFullscreen);
		DisplayIIe.setMacAllowProcessSwitching(macAllowProcessSwitching);
		DisplayIIe.setSdlTextInputMode(textInputMode);
		DisplayIIe.setSdlFullscreenMode(sdlFullscreenMode);
		DisplayIIe.setSdlImeUiSelfImplemented(sdlImeUiSelf);
		DisplayIIe.setSdlTextAnchorDebug(debugLogging);
		DisplayIIe.setSdlMouseDebug(mouseDebug);
		if( debugLogging ) {
			System.err.println("[debug] launch_config windowBackend=sdl"+
					" startFullscreen="+startFullscreen+
					" macAllowProcessSwitching="+macAllowProcessSwitching+
					" mouseDebug="+mouseDebug+
					" sdlImeUiSelf="+sdlImeUiSelf+
					" textInputMode="+textInputMode+
					" sdlFullscreenMode="+sdlFullscreenMode);
		}
			if( !debugLogging && !printTextAtExit && lastFrameOut==null )
				System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		tracePhase = tracePhase.trim().toLowerCase();
		if( !"pre".equals(tracePhase) && !"post".equals(tracePhase) )
			throw new IllegalArgumentException("Unsupported --trace-phase value: "+tracePhase+" (expected pre or post)");
		if( startupCpuManagerCalls<0L )
			throw new IllegalArgumentException("--startup-cpu-manager-calls must be >= 0");
		if( startupDisplayCycles<0L )
			throw new IllegalArgumentException("--startup-display-cycles must be >= 0");
		if( !"wdc".equals(cpuProfile) && !"cmd".equals(cpuProfile) )
			throw new IllegalArgumentException("Unsupported --cpu-profile value: "+cpuProfile+" (expected cmd or wdc)");
		Integer phaseOverride = displayPhaseCycles;
		if( phaseOverride!=null ) {
			if( phaseOverride.intValue()<0 )
				throw new IllegalArgumentException("Display phase cycles must be >= 0");
			System.setProperty("ever2e.headless.vbl.phaseCycles", Integer.toString(phaseOverride.intValue()));
		}
		System.out.println("Loading \""+propertiesFile+"\" into memory");
		VirtualMachineProperties properties = new VirtualMachineProperties(propertiesFile);

		double cpuMultiplier = Double.parseDouble(properties.getProperty("machine.cpu.mult", "1")); // 1020484hz
		// Keep keyboard manager timing at Apple IIe-like repeat cadence.
		// Key hold delay/repeat logic in KeyboardIIe.cycle() depends on this rate.
		double keyActionMultiplier = 1./17030.;
		if( cpuMultiplier<=0d )
			throw new IllegalArgumentException("machine.cpu.mult must be > 0, got "+cpuMultiplier);

		double cpuClock = 1020484d;  // 1020484hz
		double unitsPerCycle = (1000l<<GRANULARITY_BITS_PER_MS)/cpuClock;  // 20 bits per ms granularity
		double displayMultiplier = 1d;  // 59.92fps

		PriorityQueue<HardwareManager> hardwareManagerQueue = new PriorityQueue<>();

		System.out.println(properties);
		System.out.println("CPU Profile: "+cpuProfile);

		// Set up machine based on layout selection
		
		byte[] rom16k = new byte[0x4000];
		Memory8 memory = new Memory8(0x20000);
		MemoryBus8 bus;
		Cpu65c02 cpu;
			KeyboardIIe keyboard = null;
			HeadlessVideoProbe headlessProbe = null;
		if( properties.getLayout()==MachineLayoutType.DEMO_32x32 ) {
			bus = new MemoryBusDemo8(memory, keyboard);
			bus.coldReset();
			cpuMultiplier /= 32d;
			displayMultiplier = 60d/cpuClock;
			hardwareManagerQueue.add(cpu = createCpu(cpuProfile, (MemoryBusIIe) bus, (long) (unitsPerCycle/cpuMultiplier)));
			keyboard = new KeyboardIIe((long) (unitsPerCycle/keyActionMultiplier), cpu);
			cpu.getRegister().setA(0);
			cpu.getRegister().setX(0);
			cpu.getRegister().setY(0);
			cpu.getRegister().setS(0);
			cpu.getRegister().setP(0);
			hardwareManagerQueue.add(new Display32x32(bus, keyboard, (long) (unitsPerCycle/displayMultiplier)));
			hardwareManagerQueue.add(keyboard);
		} else if( properties.getLayout()==MachineLayoutType.DEMO_32x32_CONSOLE ) {
			displayMultiplier = 10d/cpuClock;
			bus = new MemoryBusDemo8(memory, null);
			bus.coldReset();
			cpuMultiplier /= 32d;
			hardwareManagerQueue.add(cpu = createCpu(cpuProfile, (MemoryBusIIe) bus, (long) (unitsPerCycle/cpuMultiplier)));
			cpu.getRegister().setA(0);
			cpu.getRegister().setX(0);
			cpu.getRegister().setY(0);
			cpu.getRegister().setS(0);
			cpu.getRegister().setP(0);
			hardwareManagerQueue.add(new Display32x32Console(bus, (long) (unitsPerCycle/displayMultiplier)));
		} else if( properties.getLayout()==MachineLayoutType.DEBUG_65C02 ) {
			bus = new MemoryBusIIe(memory, rom16k);
			bus.coldReset();
			hardwareManagerQueue.add(cpu = createCpu(cpuProfile, (MemoryBusIIe) bus, (long) (unitsPerCycle/cpuMultiplier)));
			cpu.coldReset();
			keyboard = new KeyboardIIe((long) (unitsPerCycle/keyActionMultiplier), cpu);
			hardwareManagerQueue.add(new DisplayConsoleDebug(cpu, (long) (unitsPerCycle/cpuMultiplier)));
			((MemoryBusIIe) bus).setKeyboard(keyboard);
			((MemoryBusIIe) bus).setDisplay(null);
			hardwareManagerQueue.add(keyboard);
		} else {
			bus = new MemoryBusIIe(memory, rom16k);
			bus.coldReset();
			hardwareManagerQueue.add(cpu = createCpu(cpuProfile, (MemoryBusIIe) bus, (long) (unitsPerCycle/cpuMultiplier)));
			cpu.coldReset();
			keyboard = new KeyboardIIe((long) (unitsPerCycle/keyActionMultiplier), cpu);
			VideoSignalSource display = null;
			if( textConsole ) {
				headlessProbe = new HeadlessVideoProbe((MemoryBusIIe) bus, (long) (unitsPerCycle/displayMultiplier));
				display = headlessProbe;
				hardwareManagerQueue.add(headlessProbe);
				hardwareManagerQueue.add(new DisplayConsoleAppleIIe((MemoryBusIIe) bus, (long) (unitsPerCycle/displayMultiplier)));
			}
			else if( isHeadlessMode() ) {
				System.out.println("Running headless: using headless video probe");
				headlessProbe = new HeadlessVideoProbe((MemoryBusIIe) bus, (long) (unitsPerCycle/displayMultiplier));
				display = headlessProbe;
				hardwareManagerQueue.add(headlessProbe);
			}
			else {
				DisplayIIe windowDisplay = new DisplayIIe((MemoryBusIIe) bus, keyboard, (long) (unitsPerCycle/displayMultiplier));
				windowDisplay.setShowFps(showFps);
				display = windowDisplay;
				hardwareManagerQueue.add(windowDisplay);
			}
				if( noSound ) {
					System.out.println("Audio disabled: --no-sound");
				}
				else {
					try {
						hardwareManagerQueue.add(new Speaker1Bit((MemoryBusIIe) bus, (long) unitsPerCycle, GRANULARITY_BITS_PER_MS));
					} catch (Exception e) {
						System.out.println("Warning: Speaker initialization unavailable: " + e.getClass().getSimpleName());
					}
				}
			((MemoryBusIIe) bus).setKeyboard(keyboard);
			((MemoryBusIIe) bus).setDisplay(display);
			hardwareManagerQueue.add(keyboard);
		}

		loadProgramImage(properties, memory, bus, rom16k);

		System.out.println();
		
		// Add peripherals in slots 1-7
		if( bus instanceof MemoryBusIIe )
			for( int slot = 1; slot<=7; slot++ ) {
				PeripheralIIe card = null;
				try {
					String peripheralClass = properties.getSlotLayout(slot);
					if( peripheralClass!=null )
						peripheralClass = "peripherals."+peripheralClass;
					PeripheralIIe peripheralCard = peripheralClass==null ? null :
							(PeripheralIIe) Class.forName(peripheralClass).
							getConstructor(int.class, long.class, VirtualMachineProperties.class).newInstance(
									slot, (long) unitsPerCycle, properties);
					card = ((MemoryBusIIe) bus).setSlot(slot, peripheralCard);
					((MemoryBusIIe) bus).setSlotRom(slot, card==null ? null:card.getRom256b());
					if( card!=null )
						hardwareManagerQueue.add(card);
				} catch ( Exception e ) {
					if( e.getCause()!=null && Exception.class.isInstance(e) )
						e = (Exception) e.getCause();
					System.out.println("Warning: Unable to load peripheral class for slot "+slot+
							(e.getLocalizedMessage()==null?"":":\n"+e.getLocalizedMessage()));
					((MemoryBusIIe) bus).resetSlot(slot);
					((MemoryBusIIe) bus).setSlotRom(slot, null);
				} finally {
					System.out.println("Slot "+slot+": "+(card==null?"empty":card));
				}
		}
		
		Emulator emulator = new Emulate65c02(hardwareManagerQueue, GRANULARITY_BITS_PER_MS);
		boolean runningHeadless = isHeadlessMode();
		if( runningHeadless )
			emulator.setRealtimeThrottleEnabled(false);
		if( ENABLE_STARTUP_JIT_PRIME && !noSound ) {
			try {
				runSilently(() -> emulator.startWithStepPhases(STARTUP_JIT_PRIME_STEPS, cpu, (step, manager, preCycle) -> true));
			}
			catch( Exception e ) {
				if( e instanceof HardwareException )
					throw (HardwareException) e;
				if( e instanceof InterruptedException )
					throw (InterruptedException) e;
				if( e instanceof IOException )
					throw (IOException) e;
				throw new RuntimeException("Startup JIT prime failed", e);
			}
		}

		System.out.println();
		System.out.println("--------------------------------------");
		System.out.println("          Starting Emulation          ");
		System.out.println("--------------------------------------");
		System.out.println();

			if( pasteFile!=null ) {
				if( keyboard==null )
					throw new IllegalArgumentException("--paste-file requires a machine layout with KeyboardIIe");
				pasteText = new String(Files.readAllBytes(Paths.get(pasteFile)), StandardCharsets.UTF_8);
			}
		DecimalFormat format = new DecimalFormat("0.######E0");
		HardwareManager[] managerList = new HardwareManager[hardwareManagerQueue.size()];
		for( HardwareManager manager : hardwareManagerQueue.toArray(managerList) )
			System.out.println(manager.getClass().getSimpleName()+"@"+
					format.format(cpuClock*unitsPerCycle/manager.getUnitsPerCycle())+"Hz");

		System.out.println("");
	   	if( resetPFlagValue!=null )
	   		cpu.setResetPOverride(resetPFlagValue);
	   	if( resetAValue!=null )
	   		cpu.setResetAOverride(resetAValue);
	   	if( resetXValue!=null )
	   		cpu.setResetXOverride(resetXValue);
	   	if( resetYValue!=null )
	   		cpu.setResetYOverride(resetYValue);
	   	if( resetSValue!=null )
	   		cpu.setResetSOverride(resetSValue);
	   	if( !monitorSequenceWrites.isEmpty() ) {
	   		for( int[] write : monitorSequenceWrites )
	   			bus.setByte(write[0], write[1]);
	   		System.out.println("Applied monitor sequence write(s): "+formatMonitorSequenceWrites(monitorSequenceWrites));
	   	}
	   	if( startupDisplayCycles>0L && headlessProbe!=null ) {
	   		if( startupDisplayCycles>Integer.MAX_VALUE )
	   			throw new IllegalArgumentException("--startup-display-cycles too large for headless probe");
	   		headlessProbe.advanceCycles((int) startupDisplayCycles);
	   		System.out.println("Startup pre-advance: "+startupDisplayCycles+" display cycle(s)");
	   	}
	   	if( startupCpuManagerCalls>0L ) {
	   		long warmed = emulator.start(startupCpuManagerCalls, cpu, null);
	   		System.out.println("Startup pre-run: "+warmed+" CPU manager calls");
	   	}
		if( pasteText!=null ) {
			queueBasicText(keyboard, pasteFile, pasteText);
		}
	   	if( maxCpuSteps>=0 ) {
	   		PrintWriter traceWriter = null;
	   		if( traceFile!=null ) {
	   			traceWriter = new PrintWriter(new FileWriter(traceFile));
	   			if( traceKeyValue )
	   				traceWriter.println("# trace_kv step retired_step event_type event pc opcode opc1 opc2 opc3 a x y p s mnemonic mode cpu_total_cycles cpu_micro_total_cycles subcycle_index last_instruction_cycles display_hscan display_vscan display_vbl scan_h scan_v scan_x scan_y scan_cycles_desc frame_cycle");
	   			else
	   				traceWriter.println("step,retired_step,event_type,event,pc,opcode,opc1,opc2,opc3,a,x,y,p,s,mnemonic,mode,cpu_total_cycles,cpu_micro_total_cycles,subcycle_index,last_instruction_cycles,display_hscan,display_vscan,display_vbl,scan_h,scan_v,scan_x,scan_y,scan_cycles_desc,frame_cycle");
	   		}
	   		final PrintWriter finalTraceWriter = traceWriter;
	   		final String finalTracePhase = tracePhase;
	   		final boolean finalTraceSubinstructions = traceSubinstructions;
	   		final boolean finalTraceKeyValue = traceKeyValue;
	   		final Integer finalTraceStartPc = traceStartPc;
	   		final Set<Integer> finalHaltExecutions = haltExecutions;
	   		final HeadlessVideoProbe finalHeadlessProbe = headlessProbe;
	   		final KeyboardIIe finalKeyboard = keyboard;
	   		final boolean[] haltedAtAddress = new boolean[] { false };
	   		final int[] haltedAtPc = new int[] { -1 };
	   		final boolean[] traceStarted = new boolean[] { finalTraceStartPc==null };
	   		final long[] traceStepBase = new long[] { -1L };
	   		final long[] traceRowStep = new long[] { 0L };
	   		final long[] retiredInstructionStep = new long[] { 0L };
	   		final int[] subCycleIndex = new int[] { 0 };
	   		final String[] lastRetiredSignature = new String[] { null };
	   		final boolean[] cpuStepPreWasSub = new boolean[] { false };
	   		final boolean finalDebugLogging = debugLogging;
	   		long steps = emulator.startWithStepPhases(maxCpuSteps, cpu, (step, manager, preCycle) -> {
	   			if( manager==cpu && preCycle )
	   				cpuStepPreWasSub[0] = cpu.hasPendingInFlightMicroEvent();
	   			if( finalTraceWriter==null && finalHaltExecutions.isEmpty() )
	   				return true;
	   			if( !traceStarted[0] && preCycle && finalTraceStartPc!=null &&
	   					(cpu.getPendingPC()&0xffff)==(finalTraceStartPc&0xffff) ) {
	   				traceStarted[0] = true;
	   				traceStepBase[0] = step;
	   			}
	   			if( traceStarted[0] && traceStepBase[0]<0 )
	   				traceStepBase[0] = step;
	   			int pendingPc = cpu.getPendingPC()&0xffff;
	   			int currentPc = cpu.getRegister().getPC()&0xffff;
	   			boolean hitStopAddress = manager==cpu && !finalHaltExecutions.isEmpty() &&
	   					(preCycle ? finalHaltExecutions.contains(pendingPc) : finalHaltExecutions.contains(currentPc));
	   			if( hitStopAddress && !"pre".equals(finalTracePhase) ) {
	   				haltedAtAddress[0] = true;
	   				haltedAtPc[0] = preCycle ? pendingPc : currentPc;
	   				return false;
	   			}
	   			if( hitStopAddress && finalTraceWriter!=null && "pre".equals(finalTracePhase) ) {
	   				Opcode opcode = cpu.getPendingOpcode();
	   				int traceInstructionCycles = opcode.getCycleTime();
	   				Integer machineCode = opcode.getMachineCode();
	   				String opc1 = getTraceByteHex(bus, cpu.getPendingPC() & 0xffff);
	   				String opc2 = getTraceByteHex(bus, (cpu.getPendingPC()+1) & 0xffff);
	   				String opc3 = getTraceByteHex(bus, (cpu.getPendingPC()+2) & 0xffff);
	   				String mnemonic = opcode.getMnemonic()==null ? "" : opcode.getMnemonic().toString().trim();
	   				boolean isResetEvent = "RES".equals(mnemonic);
		   				String retiredSig =
		   						Cpu65c02.getHexString(cpu.getPendingPC(), 4) + "|" +
		   						Cpu65c02.getHexString(cpu.getRegister().getA(), 2) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getX(), 2) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getY(), 2) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getP(), 2) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getS(), 2) + "|" +
	   						cpu.getTotalCycleCount() + "|" +
		   						traceInstructionCycles + "|" +
		   						(machineCode==null ? "--" : Cpu65c02.getHexString(machineCode, 2));
		   				boolean isSubinstruction = finalTraceSubinstructions && cpu.hasPendingInFlightMicroEvent();
		   				String eventType = isSubinstruction ? "sub" : (isResetEvent ? "event":"instr");
		   				String event = isSubinstruction ? "MICRO" : (isResetEvent ? "RESET":"");
		   				boolean emitRow = finalTraceSubinstructions || isResetEvent || !retiredSig.equals(lastRetiredSignature[0]);
		   				if( "instr".equals(eventType) && emitRow )
		   					retiredInstructionStep[0]++;
		   				if( ("instr".equals(eventType) || "event".equals(eventType)) && emitRow )
		   					lastRetiredSignature[0] = retiredSig;
		   				if( !emitRow ) {
		   					haltedAtAddress[0] = true;
		   					haltedAtPc[0] = pendingPc;
		   					return false;
		   				}
		   				traceRowStep[0]++;
		   				String displayHScanText = "";
		   				String displayVScanText = "";
		   				String displayVblText = "";
		   				String scanHText = "";
		   				String scanVText = "";
		   				String scanXText = "";
		   				String scanYText = "";
		   				String scanCyclesDescText = "";
		   				String traceFrameCycleText = "";
		   				int frameCycle = getDisplayFrameCycle(bus);
		   				int displayHScan = displayHScan(frameCycle);
		   				int displayVScan = displayVScan(frameCycle);
		   				int displayVbl = displayVblBit(frameCycle);
		   				int scanX = displayScanX(frameCycle);
		   				int scanY = displayScanY(frameCycle);
		   				int scanCyclesDesc = displayScanCyclesDesc(frameCycle);
		   				int traceFrameCycle = traceFrameCycle(frameCycle);
		   				displayHScanText = Integer.toString(displayHScan);
		   				displayVScanText = Integer.toString(displayVScan);
		   				displayVblText = Integer.toString(displayVbl);
		   				scanHText = Integer.toString(displayHScan);
		   				scanVText = Integer.toString(displayVScan);
		   				scanXText = Integer.toString(scanX);
		   				scanYText = Integer.toString(scanY);
		   				scanCyclesDescText = Integer.toString(scanCyclesDesc);
		   				traceFrameCycleText = Integer.toString(traceFrameCycle);
		   				int rowSubCycleIndex = "sub".equals(eventType) ? (++subCycleIndex[0]) : ("instr".equals(eventType) ? (subCycleIndex[0] + 1) : 0);
		   				if( !"sub".equals(eventType) )
		   					subCycleIndex[0] = 0;
		   				writeTraceRow(
		   						finalTraceWriter,
		   						finalTraceKeyValue,
		   						Long.toString(traceRowStep[0]),
		   						Long.toString(retiredInstructionStep[0]),
		   						eventType,
		   						event,
		   						Cpu65c02.getHexString(cpu.getPendingPC(), 4),
		   						(machineCode==null?"--":Cpu65c02.getHexString(machineCode, 2)),
		   						opc1,
		   						opc2,
		   						opc3,
		   						Cpu65c02.getHexString(cpu.getRegister().getA(), 2),
		   						Cpu65c02.getHexString(cpu.getRegister().getX(), 2),
		   						Cpu65c02.getHexString(cpu.getRegister().getY(), 2),
		   						Cpu65c02.getHexString(cpu.getRegister().getP(), 2),
		   						Cpu65c02.getHexString(cpu.getRegister().getS(), 2),
		   						opcode.getMnemonic().toString(),
		   						opcode.getAddressMode().toString(),
		   						Long.toString(cpu.getTotalCycleCount()),
		   						Long.toString(step),
		   						Integer.toString(rowSubCycleIndex),
		   						Integer.toString(traceInstructionCycles),
		   						displayHScanText,
		   						displayVScanText,
		   						displayVblText,
		   						scanHText,
		   						scanVText,
		   						scanXText,
		   						scanYText,
		   						scanCyclesDescText,
		   						traceFrameCycleText
		   				);
	   				haltedAtAddress[0] = true;
	   				haltedAtPc[0] = pendingPc;
	   				return false;
	   			}
	   			if( hitStopAddress && finalTraceWriter==null && "pre".equals(finalTracePhase) ) {
	   				haltedAtAddress[0] = true;
	   				haltedAtPc[0] = pendingPc;
	   				return false;
	   			}
	   			if( !traceStarted[0] )
	   				return true;
	   			// Trace rows should represent CPU instruction phases only.
	   			// Non-CPU managers can run between instruction boundaries and would
	   			// otherwise inflate step counts with duplicate CPU state rows.
	   			if( manager!=cpu )
	   				return true;
	   			if( "pre".equals(finalTracePhase) && !preCycle )
	   				return true;
	   			if( "post".equals(finalTracePhase) && preCycle )
	   				return true;
	   			Opcode opcode;
	   			int pc;
	   			boolean isResetEvent;
	   			boolean isSubinstruction = false;
	   			if( "pre".equals(finalTracePhase) ) {
	   				opcode = cpu.getPendingOpcode();
	   				pc = cpu.getPendingPC();
	   				isSubinstruction = finalTraceSubinstructions && cpu.hasPendingInFlightMicroEvent();
	   				String mnemonic = opcode.getMnemonic()==null ? "" : opcode.getMnemonic().toString().trim();
	   				isResetEvent = !isSubinstruction && "RES".equals(mnemonic);
	   			}
	   			else {
	   				isSubinstruction = finalTraceSubinstructions && cpuStepPreWasSub[0];
	   				Opcode executedOpcode = cpu.getOpcode();
	   				String executedMnemonic = executedOpcode.getMnemonic()==null ? "" : executedOpcode.getMnemonic().toString().trim();
	   				isResetEvent = !isSubinstruction && "RES".equals(executedMnemonic);
	   				if( isSubinstruction ) {
	   					opcode = cpu.getPendingOpcode();
	   					pc = cpu.getPendingPC();
	   				}
	   				else if( isResetEvent ) {
	   					opcode = executedOpcode;
	   					pc = cpu.getRegister().getPC();
	   				}
	   				else {
	   					opcode = cpu.getPendingOpcode();
	   					pc = cpu.getPendingPC();
	   				}
	   			}
	   			if( finalTraceWriter!=null ) {
	   				String eventType = isSubinstruction ? "sub" : (isResetEvent ? "event":"instr");
	   				String event = isSubinstruction ? "MICRO" : (isResetEvent ? "RESET":"");
	   				int traceInstructionCycles =
	   						"pre".equals(finalTracePhase) ? opcode.getCycleTime() : cpu.getLastInstructionCycleCount();
	   				Integer machineCode = opcode.getMachineCode();
	   				String opc1 = getTraceByteHex(bus, pc & 0xffff);
	   				String opc2 = getTraceByteHex(bus, (pc+1) & 0xffff);
	   				String opc3 = getTraceByteHex(bus, (pc+2) & 0xffff);
		   				String retiredSig =
		   						Cpu65c02.getHexString(pc, 4) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getA(), 2) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getX(), 2) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getY(), 2) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getP(), 2) + "|" +
	   						Cpu65c02.getHexString(cpu.getRegister().getS(), 2) + "|" +
	   						cpu.getTotalCycleCount() + "|" +
		   						traceInstructionCycles + "|" +
		   						(machineCode==null ? "--" : Cpu65c02.getHexString(machineCode, 2));
		   				boolean emitRow = finalTraceSubinstructions || "event".equals(eventType) || !retiredSig.equals(lastRetiredSignature[0]);
		   				if( "instr".equals(eventType) && emitRow )
		   					retiredInstructionStep[0]++;
		   				if( ("instr".equals(eventType) || "event".equals(eventType)) && emitRow )
		   					lastRetiredSignature[0] = retiredSig;
		   				if( !emitRow )
		   					return true;
		   				traceRowStep[0]++;
		   				String displayHScanText = "";
		   				String displayVScanText = "";
		   				String displayVblText = "";
		   				String scanHText = "";
		   				String scanVText = "";
		   				String scanXText = "";
		   				String scanYText = "";
		   				String scanCyclesDescText = "";
		   				String traceFrameCycleText = "";
		   				int frameCycle = getDisplayFrameCycle(bus);
		   				int displayHScan = displayHScan(frameCycle);
		   				int displayVScan = displayVScan(frameCycle);
		   				int displayVbl = displayVblBit(frameCycle);
		   				int scanX = displayScanX(frameCycle);
		   				int scanY = displayScanY(frameCycle);
		   				int scanCyclesDesc = displayScanCyclesDesc(frameCycle);
		   				int traceFrameCycle = traceFrameCycle(frameCycle);
		   				displayHScanText = Integer.toString(displayHScan);
		   				displayVScanText = Integer.toString(displayVScan);
		   				displayVblText = Integer.toString(displayVbl);
		   				scanHText = Integer.toString(displayHScan);
		   				scanVText = Integer.toString(displayVScan);
		   				scanXText = Integer.toString(scanX);
		   				scanYText = Integer.toString(scanY);
		   				scanCyclesDescText = Integer.toString(scanCyclesDesc);
		   				traceFrameCycleText = Integer.toString(traceFrameCycle);
		   				int rowSubCycleIndex = "sub".equals(eventType) ? (++subCycleIndex[0]) : ("instr".equals(eventType) ? (subCycleIndex[0] + 1) : 0);
		   				if( !"sub".equals(eventType) )
		   					subCycleIndex[0] = 0;
		   				writeTraceRow(
		   						finalTraceWriter,
		   						finalTraceKeyValue,
		   						Long.toString(traceRowStep[0]),
		   						Long.toString(retiredInstructionStep[0]),
		   						eventType,
		   						event,
		   						Cpu65c02.getHexString(pc, 4),
		   						(machineCode==null?"--":Cpu65c02.getHexString(machineCode, 2)),
		   						opc1,
		   						opc2,
		   						opc3,
		   						Cpu65c02.getHexString(cpu.getRegister().getA(), 2),
		   						Cpu65c02.getHexString(cpu.getRegister().getX(), 2),
		   						Cpu65c02.getHexString(cpu.getRegister().getY(), 2),
		   						Cpu65c02.getHexString(cpu.getRegister().getP(), 2),
		   						Cpu65c02.getHexString(cpu.getRegister().getS(), 2),
		   						opcode.getMnemonic().toString(),
		   						opcode.getAddressMode().toString(),
		   						Long.toString(cpu.getTotalCycleCount()),
		   						Long.toString(step),
		   						Integer.toString(rowSubCycleIndex),
		   						Integer.toString(traceInstructionCycles),
		   						displayHScanText,
		   						displayVScanText,
		   						displayVblText,
		   						scanHText,
		   						scanVText,
		   						scanXText,
		   						scanYText,
		   						scanCyclesDescText,
		   						traceFrameCycleText
		   				);
	   			}
	   			return true;
	   		});
	   		if( traceWriter!=null )
	   			traceWriter.close();
			System.out.println("Stopped after "+steps+" CPU steps");
			if( !haltExecutions.isEmpty() && haltedAtAddress[0] )
				System.out.println("Stopped at PC="+Cpu65c02.getHexString(haltedAtPc[0], 4));
			System.out.println("PC="+Cpu65c02.getHexString(cpu.getRegister().getPC(), 4)+
					" A="+Cpu65c02.getHexString(cpu.getRegister().getA(), 2)+
					" X="+Cpu65c02.getHexString(cpu.getRegister().getX(), 2)+
					" Y="+Cpu65c02.getHexString(cpu.getRegister().getY(), 2)+
					" P="+Cpu65c02.getHexString(cpu.getRegister().getP(), 2)+
					" S="+Cpu65c02.getHexString(cpu.getRegister().getS(), 2));
			if( printCpuStateAtExit )
				printCpuState(maxCpuSteps, steps, haltedAtAddress[0], haltedAtPc[0], cpu);
			if( dumpPageAddress!=null )
				printPageDump(bus, dumpPageAddress);
			if( dumpRangeStart>=0 )
				printRangeDump(bus, dumpRangeStart, dumpRangeEnd);
			if( dumpAllMapped )
				printRangeDump(bus, 0x0000, 0xffff);
			if( dumpAllRawRam )
				printRawRangeDump(memory, 0x00000, 0x1ffff);
			if( !requireHaltPcs.isEmpty() ) {
				int finalPc = haltedAtAddress[0] ? (haltedAtPc[0]&0xffff) : (cpu.getRegister().getPC()&0xffff);
				if( !requireHaltPcs.contains(finalPc) ) {
					System.err.println("Error: final PC did not match required value(s). PC="+Cpu65c02.getHexString(finalPc, 4));
					System.exit(2);
				}
			}
			if( pasteFile!=null && keyboard!=null )
				System.out.println("basic_queue queued="+keyboard.getQueuedKeyCount()+
						" consumed="+keyboard.getConsumedQueuedKeyCount()+
						" remaining="+keyboard.getQueuedKeyDepth());
				if( traceFile!=null )
					System.out.println("Trace written: "+traceFile);
				if( printTextAtExit && bus instanceof MemoryBusIIe )
					printTextScreen((MemoryBusIIe) bus, memory);
				if( lastFrameOut!=null && bus instanceof MemoryBusIIe )
					writeLastFrameDump((MemoryBusIIe) bus, lastFrameOut);
				// In windowed mode, GUI/event threads can keep the process alive after bounded runs.
				// Exit explicitly so `--steps` behaves as a finite run.
				if( !runningHeadless && !textConsole )
					System.exit(0);
	   	}
	   	else {
	   		final HeadlessVideoProbe finalHeadlessProbe = headlessProbe;
	   		final boolean finalDebugLogging = debugLogging;
	   		emulator.startWithStepPhases(-1, cpu, (step, manager, preCycle) -> {
	   			return true;
	   		});
			System.out.println("Done");
			if( printCpuStateAtExit )
				printCpuState(maxCpuSteps, -1, false, -1, cpu);
			if( dumpPageAddress!=null )
				printPageDump(bus, dumpPageAddress);
			if( dumpRangeStart>=0 )
				printRangeDump(bus, dumpRangeStart, dumpRangeEnd);
			if( dumpAllMapped )
				printRangeDump(bus, 0x0000, 0xffff);
			if( dumpAllRawRam )
				printRawRangeDump(memory, 0x00000, 0x1ffff);
			if( printTextAtExit && bus instanceof MemoryBusIIe )
				printTextScreen((MemoryBusIIe) bus, memory);
			if( lastFrameOut!=null && bus instanceof MemoryBusIIe )
				writeLastFrameDump((MemoryBusIIe) bus, lastFrameOut);
	   	}

	}

	private static int getDisplayFrameCycle(MemoryBus8 bus) {
		if( !(bus instanceof MemoryBusIIe) )
			return -1;
		VideoSignalSource display = ((MemoryBusIIe) bus).getDisplay();
		if( display==null )
			return -1;
		int h = display.getHScan();
		int v = display.getVScan();
		if( h<0 || v<0 )
			return -1;
		return v * DISPLAY_HSCAN_CYCLES + h;
	}

	private static int displayHScan(int frameCycle) {
		if( frameCycle<0 )
			return -1;
		return frameCycle % DISPLAY_HSCAN_CYCLES;
	}

	private static int displayVScan(int frameCycle) {
		if( frameCycle<0 )
			return -1;
		return (frameCycle / DISPLAY_HSCAN_CYCLES) % DISPLAY_VSCAN_LINES;
	}

	private static int displayVblBit(int frameCycle) {
		int v = displayVScan(frameCycle);
		if( v<0 )
			return 0;
		return v<DISPLAY_VBL_LINES ? 1 : 0;
	}

	private static int displayScanX(int frameCycle) {
		int h = displayHScan(frameCycle);
		if( h<0 )
			return -1;
		return h * 14;
	}

	private static int displayScanY(int frameCycle) {
		return displayVScan(frameCycle);
	}

	private static int displayScanCyclesDesc(int frameCycle) {
		int normalized = normalizeDisplayFrameCycle(frameCycle);
		if( normalized<0 )
			return -1;
		int frameLen = DISPLAY_HSCAN_CYCLES * DISPLAY_VSCAN_LINES;
		return (frameLen - 1) - normalized;
	}

	private static int normalizeDisplayFrameCycle(int frameCycle) {
		if( frameCycle<0 )
			return -1;
		int frameLen = DISPLAY_HSCAN_CYCLES * DISPLAY_VSCAN_LINES;
		return Math.floorMod(frameCycle, frameLen);
	}

	private static int traceFrameCycle(int frameCycle) {
		// Trace field is full-frame cycle position.
		return normalizeDisplayFrameCycle(frameCycle);
	}

	private static void writeTraceRow(
			PrintWriter writer,
			boolean traceKeyValue,
			String step,
			String retiredStep,
			String eventType,
			String event,
			String pc,
			String opcode,
			String opc1,
			String opc2,
			String opc3,
			String a,
			String x,
			String y,
			String p,
			String s,
			String mnemonic,
			String mode,
			String cpuTotalCycles,
			String cpuMicroTotalCycles,
			String subCycleIndex,
			String lastInstructionCycles,
			String displayHScan,
			String displayVScan,
			String displayVbl,
			String scanH,
			String scanV,
			String scanX,
			String scanY,
			String scanCyclesDesc,
			String frameCycle) {
		if( !traceKeyValue ) {
			writer.println(
					step + "," + retiredStep + "," + eventType + "," + event + "," + pc + "," +
					opcode + "," + opc1 + "," + opc2 + "," + opc3 + "," + a + "," + x + "," + y + "," +
					p + "," + s + "," + mnemonic + "," + mode + "," + cpuTotalCycles + "," +
					cpuMicroTotalCycles + "," + subCycleIndex + "," +
					lastInstructionCycles + "," + displayHScan + "," + displayVScan + "," + displayVbl + "," +
					scanH + "," + scanV + "," + scanX + "," + scanY + "," + scanCyclesDesc + "," + frameCycle
			);
			return;
		}
		String eventText = event==null || event.trim().isEmpty() ? "--" : event.trim();
		writer.println(
				"step="+zpadDec(step, 6) +
				" retired="+zpadDec(retiredStep, 6) +
				" type="+safeToken(eventType) +
				" event="+safeToken(eventText) +
				" pc="+safeToken(pc) +
				" op="+safeToken(opcode) +
				" opc1="+safeToken(opc1) +
				" opc2="+safeToken(opc2) +
				" opc3="+safeToken(opc3) +
				" a="+safeToken(a) +
				" x="+safeToken(x) +
				" y="+safeToken(y) +
				" p="+safeToken(p) +
				" s="+safeToken(s) +
				" mnem="+safeToken(mnemonic.trim()) +
				" mode="+safeToken(mode.trim()) +
				" cpu="+zpadDec(cpuTotalCycles, 8) +
				" micro_total="+zpadDec(cpuMicroTotalCycles, 8) +
				" subidx="+zpadDec(subCycleIndex, 2) +
				" instr="+zpadDec(lastInstructionCycles, 2) +
				" h="+zpadDec(displayHScan, 2) +
				" v="+zpadDec(displayVScan, 3) +
				" vbl="+safeToken(displayVbl) +
				" scanx="+zpadDec(scanX, 3) +
				" scany="+zpadDec(scanY, 3) +
				" desc="+zpadDec(scanCyclesDesc, 2) +
				" frame="+zpadDec(frameCycle, 5)
		);
	}

	private static String safeToken(String value) {
		String text = value==null ? "" : value.trim();
		if( text.isEmpty() )
			return "--";
		return text.replace(' ', '_');
	}

	private static String zpadDec(String value, int width) {
		String text = value==null ? "" : value.trim();
		if( text.isEmpty() )
			return "";
		boolean neg = text.startsWith("-");
		String digits = neg ? text.substring(1) : text;
		boolean numeric = !digits.isEmpty();
		for( int i = 0; i<digits.length(); i++ ) {
			if( !Character.isDigit(digits.charAt(i)) ) {
				numeric = false;
				break;
			}
		}
		if( !numeric )
			return text;
		String padded = digits;
		while( padded.length()<width )
			padded = "0"+padded;
		return neg ? "-"+padded : padded;
	}

	private static String getTraceByteHex(MemoryBus8 bus, int address) {
		int value;
		if( bus instanceof MemoryBusIIe )
			value = ((MemoryBusIIe) bus).peekByteNoSideEffects(address & 0xffff);
		else
			value = bus.getByte(address & 0xffff);
		return Cpu65c02.getHexString(value, 2);
	}

	private static boolean isHeadlessMode() {
		String explicit = System.getProperty("ever2e.headless");
		return explicit!=null && Boolean.parseBoolean(explicit);
	}

	private static boolean isMemoryReadMnemonic(Cpu65c02.OpcodeMnemonic mnemonic) {
		if( mnemonic==null )
			return false;
		switch( mnemonic ) {
			case ADC:
			case AND:
			case BIT:
			case CMP:
			case CPX:
			case CPY:
			case EOR:
			case LDA:
			case LDX:
			case LDY:
			case ORA:
			case SBC:
				return true;
			default:
				return false;
		}
	}

	private static int estimatePendingReadAddress(Cpu65c02 cpu, MemoryBus8 bus) {
		Opcode opcode = cpu.getPendingOpcode();
		if( opcode==null || !isMemoryReadMnemonic(opcode.getMnemonic()) )
			return -1;
		int pc = cpu.getPendingPC() & 0xffff;
		int x = cpu.getRegister().getX() & 0xff;
		int y = cpu.getRegister().getY() & 0xff;
		switch( opcode.getAddressMode() ) {
			case ABS:
				return bus.getWord16LittleEndian((pc+1)&0xffff) & 0xffff;
			case ABS_X:
				return (bus.getWord16LittleEndian((pc+1)&0xffff) + x) & 0xffff;
			case ABS_Y:
				return (bus.getWord16LittleEndian((pc+1)&0xffff) + y) & 0xffff;
			case ZPG:
				return bus.getByte((pc+1)&0xffff) & 0xff;
			case ZPG_X:
				return (bus.getByte((pc+1)&0xffff) + x) & 0xff;
			case ZPG_Y:
				return (bus.getByte((pc+1)&0xffff) + y) & 0xff;
			case ZPG_IND: {
				int zp = bus.getByte((pc+1)&0xffff) & 0xff;
				return bus.getWord16LittleEndian(zp, 0xff) & 0xffff;
			}
			case IND_X: {
				int zp = (bus.getByte((pc+1)&0xffff) + x) & 0xff;
				return bus.getWord16LittleEndian(zp, 0xff) & 0xffff;
			}
			case IND_Y: {
				int zp = bus.getByte((pc+1)&0xffff) & 0xff;
				int base = bus.getWord16LittleEndian(zp, 0xff) & 0xffff;
				return (base + y) & 0xffff;
			}
			default:
				return -1;
		}
	}

	private static int estimatePendingPreReadCycles(Cpu65c02 cpu, MemoryBus8 bus) {
		Opcode opcode = cpu.getPendingOpcode();
		if( opcode==null || opcode.getMnemonic()==null || !isMemoryReadMnemonic(opcode.getMnemonic()) )
			return 0;
		int preCycles = Math.max(0, opcode.getCycleTime()-1);
		int pc = cpu.getPendingPC() & 0xffff;
		int x = cpu.getRegister().getX() & 0xff;
		int y = cpu.getRegister().getY() & 0xff;
		switch( opcode.getAddressMode() ) {
			case ABS_X: {
				int base = bus.getWord16LittleEndian((pc+1)&0xffff) & 0xffff;
				int indexed = (base + x) & 0xffff;
				if( (base>>8)!=(indexed>>8) && addsAbsIndexedPageCrossReadCycle(opcode.getMnemonic(), Cpu65c02.AddressMode.ABS_X) )
					preCycles++;
				break;
			}
			case ABS_Y: {
				int base = bus.getWord16LittleEndian((pc+1)&0xffff) & 0xffff;
				int indexed = (base + y) & 0xffff;
				if( (base>>8)!=(indexed>>8) && addsAbsIndexedPageCrossReadCycle(opcode.getMnemonic(), Cpu65c02.AddressMode.ABS_Y) )
					preCycles++;
				break;
			}
			case IND_Y: {
				int zp = bus.getByte((pc+1)&0xffff) & 0xff;
				int base = bus.getWord16LittleEndian(zp, 0xff) & 0xffff;
				int indexed = (base + y) & 0xffff;
				if( (base>>8)!=(indexed>>8) && opcode.getMnemonic()!=Cpu65c02.OpcodeMnemonic.STA )
					preCycles++;
				break;
			}
			default:
				break;
		}
		return preCycles;
	}

	private static boolean addsAbsIndexedPageCrossReadCycle(Cpu65c02.OpcodeMnemonic mnemonic, Cpu65c02.AddressMode mode) {
		if( mnemonic==null )
			return false;
		switch( mnemonic ) {
			case ADC:
			case AND:
			case CMP:
			case EOR:
			case LDA:
			case ORA:
			case SBC:
				return true;
			case LDY:
				return mode==Cpu65c02.AddressMode.ABS_X;
			case LDX:
				return mode==Cpu65c02.AddressMode.ABS_Y;
			default:
				return false;
		}
	}

	private static void printTextScreen(MemoryBusIIe memoryBus, Memory8 memory) {
		int page = memoryBus.isPage2() ? 2 : 1;
		System.out.println("text_screen_begin");
		for( int y = 0; y<24; y++ ) {
			StringBuilder line = new StringBuilder(40);
			for( int x = 0; x<40; x++ ) {
				int addr = DisplayConsoleAppleIIe.getAddressLo40(page, y, x);
				line.append(transliterateText(memory.getByte(addr)));
			}
			System.out.println(line.toString());
		}
		System.out.println("text_screen_end");
	}

	private static void printPageDump(MemoryBus8 bus, int address) {
		int pageBase = address & 0xff00;
		System.err.println("page_dump_begin base=$"+Cpu65c02.getHexString(pageBase, 4));
		printRangeDump(bus, pageBase, pageBase+0xff);
		System.err.println("page_dump_end");
	}

	private static void printRangeDump(MemoryBus8 bus, int startAddress, int endAddress) {
		System.err.println("range_dump_begin start=$"+Cpu65c02.getHexString(startAddress, 4)+
				" end=$"+Cpu65c02.getHexString(endAddress, 4));
		for( int rowBase = (startAddress & 0xfff0); rowBase<=endAddress; rowBase += 16 ) {
			StringBuilder line = new StringBuilder();
			line.append(Cpu65c02.getHexString(rowBase & 0xffff, 4)).append(":");
			for( int col = 0; col<16; col++ ) {
				int addr = (rowBase+col) & 0xffff;
				if( addr<startAddress || addr>endAddress ) {
					line.append(" ..");
					continue;
				}
				int value = bus instanceof MemoryBusIIe
					? ((MemoryBusIIe) bus).peekByteNoSideEffects(addr)
					: bus.getByte(addr);
				line.append(" ").append(Cpu65c02.getHexString(value, 2));
			}
			System.err.println(line);
		}
		System.err.println("range_dump_end");
	}

	private static void printRawRangeDump(Memory8 memory, int startAddress, int endAddress) {
		int maxAddress = memory.getMaxAddress() - 1;
		int clampedStart = Math.max(0, startAddress);
		int clampedEnd = Math.min(endAddress, maxAddress);
		if( clampedStart>clampedEnd )
			return;
		System.err.println("raw_range_dump_begin start=$"+Cpu65c02.getHexString(clampedStart, 5)+
				" end=$"+Cpu65c02.getHexString(clampedEnd, 5));
		for( int rowBase = (clampedStart & ~0x0f); rowBase<=clampedEnd; rowBase += 16 ) {
			StringBuilder line = new StringBuilder();
			line.append(Cpu65c02.getHexString(rowBase, 5)).append(":");
			for( int col = 0; col<16; col++ ) {
				int addr = rowBase + col;
				if( addr<clampedStart || addr>clampedEnd ) {
					line.append(" ..");
					continue;
				}
				int value = memory.getByte(addr);
				line.append(" ").append(Cpu65c02.getHexString(value, 2));
			}
			System.err.println(line);
		}
		System.err.println("raw_range_dump_end");
	}

	private static void writeLastFrameDump(MemoryBusIIe memoryBus, String outFile) throws IOException {
		int[] bytes = DisplayIIe.captureFrameBytes(memoryBus);
		final int width = 65;
		final int height = 262;
		List<String> lines = new ArrayList<>(height + 2);
		lines.add("frame_dump_begin width="+width+" height="+height+" bytes="+bytes.length);
		for( int y = 0; y<height; y++ ) {
			StringBuilder line = new StringBuilder();
			line.append(String.format("%03d:", y));
			int rowStart = y * width;
			for( int x = 0; x<width; x++ )
				line.append(' ').append(Cpu65c02.getHexString(bytes[rowStart+x], 2));
			lines.add(line.toString());
		}
		lines.add("frame_dump_end");
		Files.write(Paths.get(outFile), lines, StandardCharsets.UTF_8);
		System.out.println("Last frame written: "+outFile);
	}

	private static void printCpuState(long maxCpuSteps, long stoppedAfterSteps, boolean haltedAtAddress, int haltedAtPc, Cpu65c02 cpu) {
		System.out.println("cpu_state_begin");
		System.out.println("step_limit=" + maxCpuSteps);
		System.out.println("stopped_after_steps=" + stoppedAfterSteps);
		System.out.println("halt_requested=" + (haltedAtAddress ? 1 : 0));
		if( haltedAtAddress )
			System.out.println("halt_pc=" + Cpu65c02.getHexString(haltedAtPc, 4));
		System.out.println("registers=" + cpu.getRegister().toString());
		System.out.println("cpu_state_end");
	}

	private static char transliterateText(int ascii) {
		ascii &= 0x7f;
		if( ascii<0x20 || ascii==0x7f )
			ascii = 0x7e;
		return (char) ascii;
	}

	private static void printFlags(int origA, int origB, int value) {
		System.out.print(Cpu65c02.getHexString(value&0xff, 2)+" ");
		System.out.print((((origA^value)&(origB^value)&0x80)!=0) ? "V":"v");
		System.out.print(value==0 ? "Z":"z");
		System.out.print((value&0x80)!=0 ? "N":"n");
		System.out.println((value&0x100)!=0 ? "C":"c");

	}

	public static void testOpcode() {

		boolean carry = false;
		do {
			for( int reg_getA = 0; reg_getA<256; reg_getA++ )
				for( int mem = 0; mem<256; mem++ ) {
					System.out.print(Cpu65c02.getHexString(reg_getA&0xff, 2)+" + ");
					System.out.print(Cpu65c02.getHexString(mem&0xff, 2)+" + ");
					System.out.print((carry ? 1:0)+" = ");
					int value = mem;
					int regA = reg_getA;
					int valAdd = value;
					value += regA;
					if( !carry )
						value++;
					printFlags(regA, valAdd, value);

				}
			carry = !carry;
		} while( carry );

	}

	public static void displayOpcodes() {
		for( int x = 0; x<256; x++ )
			if( Cpu65c02.OPCODE[x].getMnemonic()==Cpu65c02.OpcodeMnemonic.NOP )
				System.out.println(x);
	}

}
