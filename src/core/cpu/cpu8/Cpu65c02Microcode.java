package core.cpu.cpu8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Byte-indexed microcode data store.
 *
 * This class intentionally avoids Opcode-class dependencies; it only exposes
 * per-opcode-byte micro-instruction descriptors.
 */
public final class Cpu65c02Microcode {

	public static final class CpuRegs {
		public int a;
		public int x;
		public int y;
		public int s;
		public int p;
		public int pc;
	}

	public static final class InternalRegs {
		public int operandLo;
		public int operandHi;
		public int effectiveAddress;
		public int zpPtrLo;
		public int zpPtrHi;
		public int temp;
		public int dataLatch;
		public int cycleIndex;
		public boolean pageCrossed;
	}

	public static final class MicroContext {
		public final CpuRegs cpu = new CpuRegs();
		public final InternalRegs internal = new InternalRegs();
	}

	public enum MicroOp {
		M_FETCH_OPCODE(0),
		M_FETCH_OPERAND_LO(1),
		M_FETCH_OPERAND_HI(2),
		M_READ_IMM_DATA(3),
		M_READ_ZP_PTR_LO(4),
		M_READ_ZP_PTR_HI(5),
		M_READ_DUMMY(6),
		M_READ_EA(7),
		M_WRITE_EA_DUMMY(8),
		M_WRITE_EA(9),
		M_INTERNAL(10);

		private final int code;

		MicroOp(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}
	}

	public enum AccessType {
		AT_NONE,
		AT_READ,
		AT_WRITE,
		AT_RMW
	}

	static final class MicroInstr {
		private final AccessType accessType;
		private final MicroOp[] noCrossScript;
		private final MicroOp[] crossScript;

		private MicroInstr(AccessType accessType, MicroOp[] noCrossScript, MicroOp[] crossScript) {
			this.accessType = accessType;
			this.noCrossScript = noCrossScript;
			this.crossScript = crossScript;
		}

		AccessType getAccessType() {
			return accessType;
		}

		MicroOp[] getCycleScript(boolean pageCrossed) {
			MicroOp[] src = pageCrossed ? crossScript : noCrossScript;
			return Arrays.copyOf(src, src.length);
		}

		boolean usesMemoryDataRead() {
			return indexOfFirstReadDataCycle(false)>=0 || indexOfFirstReadDataCycle(true)>=0;
		}

		int getOperandReadCycleOffset(boolean pageCrossed) {
			return indexOfFirstReadDataCycle(pageCrossed);
		}

		private int indexOfFirstReadDataCycle(boolean pageCrossed) {
			MicroOp[] script = pageCrossed ? crossScript : noCrossScript;
			for( int i = 0; i<script.length; i++ ) {
				MicroOp op = script[i];
				if( op==MicroOp.M_READ_IMM_DATA || op==MicroOp.M_READ_EA )
					return i;
			}
			return -1;
		}
	}

	private static final MicroInstr[] TABLE = buildTable();

	private Cpu65c02Microcode() {
	}

	private static MicroInstr[] buildTable() {
		MicroInstr[] table = new MicroInstr[256];
		for( int i = 0; i<table.length; i++ )
			table[i] = buildGenericInstruction(i);

		// LDA family from enum-owned microcode programs.
		for( Cpu65c02Opcode lda : Cpu65c02Opcode.ldaFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = lda.microcode();
			set(table, lda.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode sta : Cpu65c02Opcode.staFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = sta.microcode();
			set(table, sta.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode inc : Cpu65c02Opcode.incFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = inc.microcode();
			set(table, inc.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode dec : Cpu65c02Opcode.decFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = dec.microcode();
			set(table, dec.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode asl : Cpu65c02Opcode.aslFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = asl.microcode();
			set(table, asl.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode lsr : Cpu65c02Opcode.lsrFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = lsr.microcode();
			set(table, lsr.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode rol : Cpu65c02Opcode.rolFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = rol.microcode();
			set(table, rol.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode ror : Cpu65c02Opcode.rorFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = ror.microcode();
			set(table, ror.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode ora : Cpu65c02Opcode.oraFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = ora.microcode();
			set(table, ora.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode and : Cpu65c02Opcode.andFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = and.microcode();
			set(table, and.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode eor : Cpu65c02Opcode.eorFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = eor.microcode();
			set(table, eor.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode adc : Cpu65c02Opcode.adcFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = adc.microcode();
			set(table, adc.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode sbc : Cpu65c02Opcode.sbcFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = sbc.microcode();
			set(table, sbc.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode cmp : Cpu65c02Opcode.cmpFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = cmp.microcode();
			set(table, cmp.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode bit : Cpu65c02Opcode.bitFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = bit.microcode();
			set(table, bit.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode ldx : Cpu65c02Opcode.ldxFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = ldx.microcode();
			set(table, ldx.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode ldy : Cpu65c02Opcode.ldyFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = ldy.microcode();
			set(table, ldy.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode stx : Cpu65c02Opcode.stxFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = stx.microcode();
			set(table, stx.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode sty : Cpu65c02Opcode.styFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = sty.microcode();
			set(table, sty.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode cpx : Cpu65c02Opcode.cpxFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = cpx.microcode();
			set(table, cpx.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode cpy : Cpu65c02Opcode.cpyFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = cpy.microcode();
			set(table, cpy.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode jsr : Cpu65c02Opcode.jsrFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = jsr.microcode();
			set(table, jsr.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode branch : Cpu65c02Opcode.branchFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = branch.microcode();
			set(table, branch.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		for( Cpu65c02Opcode misc : Cpu65c02Opcode.controlMiscFamily() ) {
			Cpu65c02Opcode.MicroCycleProgram program = misc.microcode();
			set(table, misc.opcodeByte(), program.accessType(), program.noCrossScript(), program.crossScript());
		}
		return table;
	}

	private static MicroInstr buildGenericInstruction(int opcodeByte) {
		Opcode opcode = Cpu65c02.OPCODE[opcodeByte & 0xff];
		if( opcode==null ) {
			MicroOp[] fallback = script(MicroOp.M_FETCH_OPCODE);
			return new MicroInstr(AccessType.AT_NONE, fallback, fallback);
		}
		AccessType accessType = inferAccessType(opcode);
		boolean pageCrossVariant = hasPageCrossReadVariant(opcode, accessType);
		MicroOp[] noCross = buildGenericScript(opcode, accessType, false);
		MicroOp[] cross = pageCrossVariant ? buildGenericScript(opcode, accessType, true) : noCross;
		return new MicroInstr(accessType, noCross, cross);
	}

	private static AccessType inferAccessType(Opcode opcode) {
		if( opcode==null || opcode.getMnemonic()==null )
			return AccessType.AT_NONE;
		switch( opcode.getMnemonic() ) {
			case STA:
			case STX:
			case STY:
			case STZ:
				return AccessType.AT_WRITE;
			case ASL:
			case LSR:
			case ROL:
			case ROR:
			case INC:
			case DEC:
			case TRB:
			case TSB:
				return AccessType.AT_RMW;
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
				return AccessType.AT_READ;
			default:
				return AccessType.AT_NONE;
		}
	}

	private static boolean hasPageCrossReadVariant(Opcode opcode, AccessType accessType) {
		if( opcode==null || accessType!=AccessType.AT_READ )
			return false;
		switch( opcode.getAddressMode() ) {
			case ABS_X:
				switch( opcode.getMnemonic() ) {
					case ADC:
					case AND:
					case CMP:
					case EOR:
					case LDA:
					case LDY:
					case ORA:
					case SBC:
						return true;
					default:
						return false;
				}
			case ABS_Y:
				switch( opcode.getMnemonic() ) {
					case ADC:
					case AND:
					case CMP:
					case EOR:
					case LDA:
					case LDX:
					case ORA:
					case SBC:
						return true;
					default:
						return false;
				}
			case IND_Y:
				return true;
			default:
				return false;
		}
	}

	private static MicroOp[] buildGenericScript(Opcode opcode, AccessType accessType, boolean pageCrossed) {
		List<MicroOp> ops = new ArrayList<MicroOp>();
		ops.add(MicroOp.M_FETCH_OPCODE);

		switch( opcode.getAddressMode() ) {
			case IMM:
				ops.add(MicroOp.M_READ_IMM_DATA);
				break;
			case ZPG:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				appendEaAccess(ops, accessType);
				break;
			case ZPG_X:
			case ZPG_Y:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				ops.add(MicroOp.M_READ_DUMMY);
				appendEaAccess(ops, accessType);
				break;
			case ABS:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				ops.add(MicroOp.M_FETCH_OPERAND_HI);
				appendEaAccess(ops, accessType);
				break;
			case ABS_X:
			case ABS_Y:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				ops.add(MicroOp.M_FETCH_OPERAND_HI);
				if( accessType==AccessType.AT_WRITE || accessType==AccessType.AT_RMW || pageCrossed )
					ops.add(MicroOp.M_READ_DUMMY);
				appendEaAccess(ops, accessType);
				break;
			case IND_X:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				ops.add(MicroOp.M_READ_DUMMY);
				ops.add(MicroOp.M_READ_ZP_PTR_LO);
				ops.add(MicroOp.M_READ_ZP_PTR_HI);
				appendEaAccess(ops, accessType);
				break;
			case IND_Y:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				ops.add(MicroOp.M_READ_ZP_PTR_LO);
				ops.add(MicroOp.M_READ_ZP_PTR_HI);
				if( accessType==AccessType.AT_WRITE || accessType==AccessType.AT_RMW || pageCrossed )
					ops.add(MicroOp.M_READ_DUMMY);
				appendEaAccess(ops, accessType);
				break;
			case ZPG_IND:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				ops.add(MicroOp.M_READ_ZP_PTR_LO);
				ops.add(MicroOp.M_READ_ZP_PTR_HI);
				appendEaAccess(ops, accessType);
				break;
			case ABS_IND:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				ops.add(MicroOp.M_FETCH_OPERAND_HI);
				ops.add(MicroOp.M_READ_ZP_PTR_LO);
				ops.add(MicroOp.M_READ_ZP_PTR_HI);
				break;
			case ABS_IND_X:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				ops.add(MicroOp.M_FETCH_OPERAND_HI);
				ops.add(MicroOp.M_READ_DUMMY);
				ops.add(MicroOp.M_READ_ZP_PTR_LO);
				ops.add(MicroOp.M_READ_ZP_PTR_HI);
				break;
			case REL:
				ops.add(MicroOp.M_FETCH_OPERAND_LO);
				break;
			case ACC:
				ops.add(MicroOp.M_INTERNAL);
				break;
			case IMP:
				break;
			default:
				break;
		}

		int targetCycles = Math.max(1, opcode.getCycleTime());
		while( ops.size()<targetCycles )
			ops.add(MicroOp.M_INTERNAL);
		if( ops.size()>targetCycles )
			return ops.subList(0, targetCycles).toArray(new MicroOp[0]);
		return ops.toArray(new MicroOp[0]);
	}

	private static void appendEaAccess(List<MicroOp> ops, AccessType accessType) {
		switch( accessType ) {
			case AT_READ:
				ops.add(MicroOp.M_READ_EA);
				break;
			case AT_WRITE:
				ops.add(MicroOp.M_WRITE_EA);
				break;
			case AT_RMW:
				ops.add(MicroOp.M_READ_EA);
				ops.add(MicroOp.M_WRITE_EA_DUMMY);
				ops.add(MicroOp.M_WRITE_EA);
				break;
			case AT_NONE:
			default:
				ops.add(MicroOp.M_INTERNAL);
				break;
		}
	}

	private static void set(MicroInstr[] table, int opcodeByte, AccessType accessType, MicroOp[] noCross, MicroOp[] cross) {
		table[opcodeByte & 0xff] = new MicroInstr(accessType, noCross, cross);
	}

	private static MicroOp[] script(MicroOp... ops) {
		return ops;
	}

	static MicroInstr microInstrForByte(int opcodeByte) {
		return TABLE[opcodeByte & 0xff];
	}

	public static Cpu65c02OpcodeView opcodeForByte(int opcodeByte) {
		return new Cpu65c02OpcodeView(opcodeByte & 0xff);
	}

	public static MicroContext newContext() {
		return new MicroContext();
	}
}
