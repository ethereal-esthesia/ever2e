package test.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import core.cpu.cpu8.Cpu65c02;
import core.cpu.cpu8.Cpu65c02Cmd;
import core.cpu.cpu8.Cpu65c02Microcode;
import core.cpu.cpu8.Cpu65c02Opcode;
import core.cpu.cpu8.Cpu65c02OpcodeView;
import core.cpu.cpu8.Opcode;
import core.memory.memory8.Memory8;
import core.memory.memory8.MemoryBusIIe;

public class Cpu65c02ProfileMicrocodeCoverageTest {

	private static final int MEM_SIZE = 0x20000;
	private static final int ROM_SIZE = 0x4000;

	private static Opcode[] opcodeTableFor(Cpu65c02 cpu) {
		try {
			Method m = Cpu65c02.class.getDeclaredMethod("getOpcodeTable");
			m.setAccessible(true);
			return (Opcode[]) m.invoke(cpu);
		}
		catch( Exception e ) {
			throw new RuntimeException("Unable to resolve opcode table for profile", e);
		}
	}

	private static Cpu65c02 newWdcCpu() {
		Memory8 mem = new Memory8(MEM_SIZE);
		byte[] rom = new byte[ROM_SIZE];
		return new Cpu65c02(new MemoryBusIIe(mem, rom), 0L);
	}

	private static Cpu65c02 newCmdCpu() {
		Memory8 mem = new Memory8(MEM_SIZE);
		byte[] rom = new byte[ROM_SIZE];
		return new Cpu65c02Cmd(new MemoryBusIIe(mem, rom), 0L);
	}

	private static void assertProfileOpcodeTableHasAllSlots(Cpu65c02 cpu, String profileName) {
		Opcode[] table = opcodeTableFor(cpu);
		assertEquals(profileName + " table length", 256, table.length);
		for( int opcode = 0; opcode<256; opcode++ ) {
			assertNotNull(profileName + " opcode table missing entry for $" + String.format("%02X", opcode), table[opcode]);
		}
	}

	private static void assertProfileMicrocodeCoverage(Cpu65c02 cpu, String profileName) {
		Opcode[] table = opcodeTableFor(cpu);
		for( Cpu65c02Opcode microcoded : Cpu65c02Opcode.values() ) {
			int opcodeByte = microcoded.opcodeByte() & 0xff;
			Opcode profileOpcode = table[opcodeByte];
			assertNotNull(profileName + " profile opcode missing for $" + String.format("%02X", opcodeByte), profileOpcode);
			assertTrue(profileName + " profile must not map microcoded opcode $" + String.format("%02X", opcodeByte) + " to NOP",
					profileOpcode.getMnemonic()!=Cpu65c02.OpcodeMnemonic.NOP);

			Cpu65c02OpcodeView view = Cpu65c02Microcode.opcodeForByte(opcodeByte);
			assertNotNull("microcode view missing for $" + String.format("%02X", opcodeByte), view);
			assertEquals("opcode byte mismatch for " + profileName + " $" + String.format("%02X", opcodeByte),
					opcodeByte, view.getOpcodeByte());

			int noCrossCycles = view.getExpectedMnemonicOrder(false).length;
			int crossCycles = view.getExpectedMnemonicOrder(true).length;
			int profileCycles = profileOpcode.getCycleTime();

			assertTrue(profileName + " cycles too small for $" + String.format("%02X", opcodeByte),
					profileCycles>=noCrossCycles);
			assertTrue(profileName + " cycles not compatible with microcode paths for $" + String.format("%02X", opcodeByte),
					profileCycles==noCrossCycles || profileCycles==crossCycles || profileCycles==noCrossCycles+1);
		}
	}

	private static void assertProfileCycleCountMatchesMicrocodeSteps(Cpu65c02 cpu, String profileName, boolean includeNops, boolean requireExplicitMicrocode) {
		Opcode[] table = opcodeTableFor(cpu);
		List<String> failures = new ArrayList<String>();
		int checked = 0;
		for( int opcodeByte = 0; opcodeByte<256; opcodeByte++ ) {
			Opcode opcode = table[opcodeByte];
			if( opcode==null ) {
				failures.add(String.format("$%02X missing opcode table entry", opcodeByte));
				continue;
			}
			boolean isNop = opcode.getMnemonic()==Cpu65c02.OpcodeMnemonic.NOP;
			if( isNop!=includeNops )
				continue;
			checked++;
			if( requireExplicitMicrocode ) {
				if( Cpu65c02Opcode.fromOpcodeByte(opcodeByte)==null ) {
					failures.add(String.format("$%02X lacks explicit microcode enum entry (%s/%s)",
							opcodeByte, opcode.getMnemonic(), opcode.getAddressMode()));
					continue;
				}
			}

			Cpu65c02OpcodeView view = Cpu65c02Microcode.opcodeForByte(opcodeByte);
			if( view==null ) {
				failures.add(String.format("$%02X missing microcode view", opcodeByte));
				continue;
			}

			int baseCycles = opcode.getCycleTime();
			int noCrossCycles = view.getExpectedMnemonicOrder(false).length;
			int crossCycles = view.getExpectedMnemonicOrder(true).length;

			if( baseCycles!=noCrossCycles ) {
				failures.add(String.format("$%02X no-cross cycles mismatch exp=%d got=%d (%s/%s)",
						opcodeByte, baseCycles, noCrossCycles, opcode.getMnemonic(), opcode.getAddressMode()));
				continue;
			}
			if( crossCycles!=noCrossCycles ) {
				if( (baseCycles+1)!=crossCycles ) {
					failures.add(String.format("$%02X cross cycles mismatch exp=%d got=%d (%s/%s)",
							opcodeByte, baseCycles+1, crossCycles, opcode.getMnemonic(), opcode.getAddressMode()));
				}
			}
		}
		int passed = checked - failures.size();
		String scope = includeNops ? "nops" : "non_nops";
		System.out.println(profileName + " microcode cycle coverage (" + scope + "): checked=" + checked + ", passed=" + passed + ", failed=" + failures.size());
		if( !failures.isEmpty() ) {
			StringBuilder sample = new StringBuilder();
			int limit = Math.min(12, failures.size());
			for( int i = 0; i<limit; i++ ) {
				if( i>0 )
					sample.append("; ");
				sample.append(failures.get(i));
			}
			assertTrue(profileName + " microcode cycle coverage: checked=" + checked + ", passed=" + passed + ", failed=" + failures.size()
					+ " opcode failures. Sample: " + sample, false);
		}
	}

	@Test
	public void wdcProfileHasCompleteOpcodeTableAndMicrocodeCoverage() {
		Cpu65c02 cpu = newWdcCpu();
		assertProfileOpcodeTableHasAllSlots(cpu, "wdc");
		assertProfileMicrocodeCoverage(cpu, "wdc");
	}

	@Test
	public void cmdProfileHasCompleteOpcodeTableAndMicrocodeCoverage() {
		Cpu65c02 cpu = newCmdCpu();
		assertProfileOpcodeTableHasAllSlots(cpu, "cmd");
		assertProfileMicrocodeCoverage(cpu, "cmd");
	}

	@Test
	public void wdcProfileAllNonNopOpcodesMatchMicrocodeCycleCount() {
		Cpu65c02 cpu = newWdcCpu();
		assertProfileCycleCountMatchesMicrocodeSteps(cpu, "wdc", false, true);
	}

	@Test
	public void cmdProfileAllNonNopOpcodesMatchMicrocodeCycleCount() {
		Cpu65c02 cpu = newCmdCpu();
		assertProfileCycleCountMatchesMicrocodeSteps(cpu, "cmd", false, true);
	}

	@Test
	public void wdcProfileNopOpcodesMatchMicrocodeCycleCount() {
		Cpu65c02 cpu = newWdcCpu();
		assertProfileCycleCountMatchesMicrocodeSteps(cpu, "wdc", true, false);
	}

	@Test
	public void cmdProfileNopOpcodesMatchMicrocodeCycleCount() {
		Cpu65c02 cpu = newCmdCpu();
		assertProfileCycleCountMatchesMicrocodeSteps(cpu, "cmd", true, false);
	}
}
