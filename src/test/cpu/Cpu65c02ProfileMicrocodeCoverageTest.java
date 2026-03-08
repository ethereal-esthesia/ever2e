package test.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

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
}
