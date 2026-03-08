package core.cpu.cpu8;

import java.util.EnumSet;

import core.memory.memory8.MemoryBusIIe;

final class CpuExecutionPlanner {

	static final class Plan {
		final int pendingCycles;
		final int totalCycles;

		Plan(int pendingCycles, int totalCycles) {
			this.pendingCycles = pendingCycles;
			this.totalCycles = totalCycles;
		}
	}

	private static final EnumSet<Cpu65c02Opcode> MICRO_QUEUED_FAMILY = buildMicroQueuedFamily();

	private final MemoryBusIIe memory;
	private final Register reg;

	CpuExecutionPlanner(MemoryBusIIe memory, Register reg) {
		this.memory = memory;
		this.reg = reg;
	}

	Plan buildPlan(Opcode op, int pc) {
		if( !isMicroQueued(op) )
			return new Plan(0, op.getCycleTime());
		int totalCycles = Cpu65c02CycleEstimator.predictInstructionCycles(memory, reg, op, pc);
		return new Plan(Math.max(0, totalCycles-1), totalCycles);
	}

	boolean isMicroQueued(Opcode op) {
		if( op==null || op.getMachineCode()==null )
			return false;
		Cpu65c02Opcode mapped = Cpu65c02Opcode.fromOpcodeByte(op.getMachineCode().intValue());
		return mapped!=null && MICRO_QUEUED_FAMILY.contains(mapped);
	}

	private static EnumSet<Cpu65c02Opcode> buildMicroQueuedFamily() {
		EnumSet<Cpu65c02Opcode> out = EnumSet.noneOf(Cpu65c02Opcode.class);
		out.addAll(Cpu65c02Opcode.ldaFamily());
		out.addAll(Cpu65c02Opcode.staFamily());
		out.addAll(Cpu65c02Opcode.incFamily());
		out.addAll(Cpu65c02Opcode.decFamily());
		out.addAll(Cpu65c02Opcode.aslFamily());
		out.addAll(Cpu65c02Opcode.lsrFamily());
		out.addAll(Cpu65c02Opcode.rolFamily());
		out.addAll(Cpu65c02Opcode.rorFamily());
		out.addAll(Cpu65c02Opcode.oraFamily());
		out.addAll(Cpu65c02Opcode.andFamily());
		out.addAll(Cpu65c02Opcode.eorFamily());
		out.addAll(Cpu65c02Opcode.adcFamily());
		out.addAll(Cpu65c02Opcode.sbcFamily());
		out.addAll(Cpu65c02Opcode.cmpFamily());
		out.addAll(Cpu65c02Opcode.bitFamily());
		out.addAll(Cpu65c02Opcode.ldxFamily());
		out.addAll(Cpu65c02Opcode.ldyFamily());
		out.addAll(Cpu65c02Opcode.stxFamily());
		out.addAll(Cpu65c02Opcode.styFamily());
		out.addAll(Cpu65c02Opcode.cpxFamily());
		out.addAll(Cpu65c02Opcode.cpyFamily());
		out.addAll(Cpu65c02Opcode.jsrFamily());
		return out;
	}
}
