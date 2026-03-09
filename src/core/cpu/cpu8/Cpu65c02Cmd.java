package core.cpu.cpu8;

import core.memory.memory8.MemoryBusIIe;

/**
 * G65SC02-compatible wrapper profile.
 * <p>
 * Uses the CMD opcode/timing table hook so we can isolate legacy-chip
 * behavior from the default CPU profile as this mapping evolves.
 */
public class Cpu65c02Cmd extends Cpu65c02 {

	private static final int[][] CMD_NOP_WIDTH_OVERRIDES = new int[][] {
			{ 0x07, 2 }, { 0x0F, 3 }, { 0x17, 2 }, { 0x1F, 3 }, { 0x27, 2 }, { 0x2F, 3 }, { 0x37, 2 }, { 0x3F, 3 },
			{ 0x47, 2 }, { 0x4F, 3 }, { 0x57, 2 }, { 0x5F, 3 }, { 0x67, 2 }, { 0x6F, 3 }, { 0x77, 2 }, { 0x7F, 3 },
			{ 0x87, 2 }, { 0x8F, 3 }, { 0x97, 2 }, { 0x9F, 3 }, { 0xA7, 2 }, { 0xAF, 3 }, { 0xB7, 2 }, { 0xBF, 3 },
			{ 0xC7, 2 }, { 0xCF, 3 }, { 0xD7, 2 }, { 0xDF, 3 }, { 0xE7, 2 }, { 0xEF, 3 }, { 0xF7, 2 }, { 0xFF, 3 }
	};
	private static final Opcode[] RETRO_OPCODE_TABLE = createRetroOpcodeTable();

	public Cpu65c02Cmd(MemoryBusIIe memory, long unitsPerCycle) {
		super(memory, unitsPerCycle);
	}

	private static Opcode[] createRetroOpcodeTable() {
		Opcode[] base = Cpu65c02.OPCODE;
		Opcode[] cmd = new Opcode[base.length];
		for( int i = 0; i<base.length; i++ ) {
			Opcode op = base[i];
			if( op==null )
				continue;
			cmd[i] = new Opcode(op.getMachineCode(), op.getMnemonic(), op.getAddressMode(), op.getInstrSize(), op.getCycleTime());
		}

		// Real-hardware validated CMD/G65SC02 NOP behavior.
		for( int[] pair : CMD_NOP_WIDTH_OVERRIDES ) {
			int opcode = pair[0] & 0xFF;
			int width = pair[1];
			Opcode op = cmd[opcode];
			cmd[opcode] = new Opcode(opcode, OpcodeMnemonic.NOP, AddressMode.IMP, width, op.getCycleTime());
		}
		cmd[0x03] = new Opcode(0x03, OpcodeMnemonic.NOP, AddressMode.IMP, 1, 1);
		cmd[0x5C] = new Opcode(0x5C, OpcodeMnemonic.NOP, AddressMode.IMP, 3, 8);
		return cmd;
	}

	@Override
	protected Opcode[] getOpcodeTable() {
		return RETRO_OPCODE_TABLE;
	}
}
