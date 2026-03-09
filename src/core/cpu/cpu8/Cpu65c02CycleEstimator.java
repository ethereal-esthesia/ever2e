package core.cpu.cpu8;

import core.cpu.cpu8.Cpu65c02.AddressMode;
import core.cpu.cpu8.Cpu65c02.OpcodeMnemonic;
import core.memory.memory8.MemoryBusIIe;

public final class Cpu65c02CycleEstimator {

	private Cpu65c02CycleEstimator() {
	}

	public static int predictInstructionCycles(MemoryBusIIe memory, Register reg, Opcode op, int pc) {
		int cycles = op.getCycleTime();
		Integer machineCode = op.getMachineCode();
		if( machineCode==null )
			return cycles;
		Cpu65c02Opcode opcode = Cpu65c02Opcode.fromOpcodeByte(machineCode.intValue());
		if( opcode==null )
			return cycles;
		int operandCounter = (pc+1)&0xffff;
		switch( op.getAddressMode() ) {
			case ABS_X: {
				if( hasAbsIndexedPageCrossPenalty(opcode, AddressMode.ABS_X) && crossesPage(memory.getWord16LittleEndian(operandCounter), reg.getX()) )
					cycles++;
				break;
			}
			case ABS_Y: {
				if( hasAbsIndexedPageCrossPenalty(opcode, AddressMode.ABS_Y) && crossesPage(memory.getWord16LittleEndian(operandCounter), reg.getY()) )
					cycles++;
				break;
			}
			case IND_Y: {
				int ptr = memory.getByte(operandCounter);
				int base = memory.getWord16LittleEndian(ptr, 0xff);
				if( hasIndYPageCrossPenalty(opcode) && crossesPage(base, reg.getY()) )
					cycles++;
				break;
			}
			default:
				break;
		}
		OpcodeMnemonic mnemonic = op.getMnemonic();
		if( reg.getP(Register.StatusRegister.D) &&
				(mnemonic==OpcodeMnemonic.ADC || mnemonic==OpcodeMnemonic.SBC) )
			cycles++;
		return cycles;
	}

	private static boolean crossesPage(int base, int add) {
		int eff = (base + add) & 0xffff;
		return (base>>8)!=(eff>>8);
	}

	private static boolean hasIndYPageCrossPenalty(Cpu65c02Opcode opcode) {
		OpcodeMnemonic mnemonic = Cpu65c02.OPCODE[opcode.opcodeByte()].getMnemonic();
		return mnemonic!=OpcodeMnemonic.STA;
	}

	private static boolean hasAbsIndexedPageCrossPenalty(Cpu65c02Opcode opcode, AddressMode mode) {
		OpcodeMnemonic mnemonic = Cpu65c02.OPCODE[opcode.opcodeByte()].getMnemonic();
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
				return mode==AddressMode.ABS_X;
			case LDX:
				return mode==AddressMode.ABS_Y;
			default:
				return false;
		}
	}

}
