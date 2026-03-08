package core.cpu.cpu8;

import core.memory.memory8.MemoryBusIIe;

/**
 * G65SC02-compatible wrapper profile.
 * <p>
 * Uses the CMD opcode/timing table hook so we can isolate legacy-chip
 * behavior from the default CPU profile as this mapping evolves.
 */
public class Cpu65c02Cmd extends Cpu65c02 {

	private static final Opcode[] RETRO_OPCODE_TABLE = Cpu65c02.OPCODE;

	public Cpu65c02Cmd(MemoryBusIIe memory, long unitsPerCycle) {
		super(memory, unitsPerCycle);
	}

	@Override
	protected Opcode[] getOpcodeTable() {
		return RETRO_OPCODE_TABLE;
	}
}
