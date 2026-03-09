package test.cpu;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import core.emulator.machine.machine8.Emulator8Coordinator;

import static org.junit.Assert.assertTrue;

public class Cpu32kLongRunIntegrationTest {

	@Test
	public void opcodeLongRun32kReachesPassLoop() throws Exception {
		String emuFile = getProp("ever2e.long32k.emu", "ever2e.smoke32k.emu", "ROMS/Apple2e.emu");
		String pasteFile = getProp("ever2e.long32k.pasteFile", "ever2e.smoke32k.pasteFile", "ROMS/opcode_smoke_loader_hgr_mem_32k.mon");
		String steps = getProp("ever2e.long32k.steps", "ever2e.smoke32k.steps", "120000000");
		String haltExecution = getProp("ever2e.long32k.haltExecution", "ever2e.smoke32k.haltExecution", "0x6A45,0x6A33");
		String cpuProfile = getProp("ever2e.long32k.cpuProfile", "ever2e.smoke32k.cpuProfile", "cmd");

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		PrintStream originalOut = System.out;
		String previousHeadless = System.getProperty("java.awt.headless");
		try {
			System.setProperty("java.awt.headless", "true");
			System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
				Emulator8Coordinator.main(new String[] {
						emuFile,
						"--steps", steps,
						"--text-console",
						"--paste-file", pasteFile,
						"--cpu-profile", cpuProfile,
						"--halt-execution", haltExecution,
						"--print-cpu-state-at-exit",
						"--print-text-at-exit",
						"--no-sound",
				});
		}
		finally {
			System.setOut(originalOut);
			if( previousHeadless==null )
				System.clearProperty("java.awt.headless");
			else
				System.setProperty("java.awt.headless", previousHeadless);
		}

		String stdout = output.toString(StandardCharsets.UTF_8);
		assertTrue("32k long-run should stop at pass loop 0x6A45.\nOutput:\n"+stdout,
				stdout.contains("Stopped at PC=6A45"));
		assertTrue("32k long-run should not stop at fail loop 0x6A33.\nOutput:\n"+stdout,
				!stdout.contains("Stopped at PC=6A33"));
	}

	private static String getProp(String primary, String legacy, String fallback) {
		String value = System.getProperty(primary);
		if( value!=null )
			return value;
		value = System.getProperty(legacy);
		if( value!=null )
			return value;
		return fallback;
	}
}
