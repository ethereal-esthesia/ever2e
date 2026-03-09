package core.cpu.cpu8;

import java.util.ArrayDeque;
import java.util.Deque;

import core.cpu.cpu8.Register.StatusRegister;
import core.exception.HardwareException;
import core.memory.memory8.MemoryBus8;
import core.memory.memory8.MemoryBusIIe;
import core.emulator.HardwareManager;

/* Copyright (C) 2012-2015 Shane Reilly
 * shane@cursorcorner.net
 *
 * This file is part of the Ever2e Application.
 *
 * This file is free software: it may be redistributed and/or
 * modified under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This file is distributed in the hope that it will be useful, but
 * without any warranty including the implied warranty of
 * merchantability or fitness for a particular purpose. See the
 * GNU General Public License for more details.
 *
 * A copy of the GNU General Public License should accompany this
 * file. If it does not, it may be found at
 * <http://www.gnu.org/licenses/>.
 */

public class Cpu65c02 extends HardwareManager {

	private int operandPtr;
	private int newPc;
	private Opcode newOpcode;
	private Opcode opcode;
	
	private Register reg = new Register();
	private MemoryBusIIe memory;

	private int cycleCount;
	private int idleCycle;
	private int lastCycleCount;
	private int lastInstructionCycleCount;
	private long totalCycleCount;
	private int pendingInstructionCyclesConsumed;
	private final Deque<CpuExecutionEvent> executionQueue = new ArrayDeque<CpuExecutionEvent>();
	private final CpuExecutionPlanner executionPlanner;

	private volatile Opcode interruptPending;
	private boolean isHalted;
	private Integer resetPOverride;
	private Integer resetAOverride;
	private Integer resetXOverride;
	private Integer resetYOverride;
	private Integer resetSOverride;

	@FunctionalInterface
	private interface CpuExecutionAction {
		void run() throws HardwareException;
	}

	private static final class CpuExecutionEvent {
		private final boolean instructionEnd;
		private final CpuExecutionAction action;

		private CpuExecutionEvent(boolean instructionEnd, CpuExecutionAction action) {
			this.instructionEnd = instructionEnd;
			this.action = action;
		}
	}
	
	private static final int STACK_PAGE = 0x100;
	
	public static final int INT_NMI_VECTOR_ADDR = 0xfffa;
	public static final int INT_RES_VECTOR_ADDR = 0xfffc;
	public static final int INT_IRQ_VECTOR_ADDR = 0xfffe;
	public static final int INT_BRK_VECTOR_ADDR = 0xfffe;

	// See Sather 4-27 for complete instruction cycle breakdown
	// See Sather C-10 for reference to NOP cycle times / sizes
	
	public static final Opcode INTERRUPT_IRQ = new Opcode( null, OpcodeMnemonic.IRQ, AddressMode.IMP, 0, 6 );  /// TODO: Verify cycle time
	public static final Opcode INTERRUPT_NMI = new Opcode( null, OpcodeMnemonic.NMI, AddressMode.IMP, 0, 6 );  /// TODO: Verify cycle time
	public static final Opcode INTERRUPT_RES = new Opcode( null, OpcodeMnemonic.RES, AddressMode.IMP, 0, 6 );
	public static final Opcode INTERRUPT_HLT = new Opcode( null, OpcodeMnemonic.HLT, AddressMode.IMP, 0, 6 );

	/// TODO: Fill these in and verify
	// * ADD 1 to N if page boundary is crossed
    // ** Add 1 to N if branch occurs to same page – Add 2 to N if branch occurs to different page
	// *** Add 1 to N if in decimal mode
	public static final Opcode OPCODE[] =
	{
		new Opcode( 0x00, OpcodeMnemonic.BRK, AddressMode.IMP,       2, 7 ),
		new Opcode( 0x01, OpcodeMnemonic.ORA, AddressMode.IND_X,     2, 6 ),
		new Opcode( 0x02, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 2 ),
		new Opcode( 0x03, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x04, OpcodeMnemonic.TSB, AddressMode.ZPG,       2, 5 ),
		new Opcode( 0x05, OpcodeMnemonic.ORA, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x06, OpcodeMnemonic.ASL, AddressMode.ZPG,       2, 5 ),
		new Opcode( 0x07, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x08, OpcodeMnemonic.PHP, AddressMode.IMP,       1, 3 ),
		new Opcode( 0x09, OpcodeMnemonic.ORA, AddressMode.IMM,       2, 2 ),
		new Opcode( 0x0a, OpcodeMnemonic.ASL, AddressMode.ACC,       1, 2 ),
		new Opcode( 0x0b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x0c, OpcodeMnemonic.TSB, AddressMode.ABS,       3, 6 ),
		new Opcode( 0x0d, OpcodeMnemonic.ORA, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x0e, OpcodeMnemonic.ASL, AddressMode.ABS,       3, 6 ),
		new Opcode( 0x0f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x10, OpcodeMnemonic.BPL, AddressMode.REL,       2, 2 ),
		new Opcode( 0x11, OpcodeMnemonic.ORA, AddressMode.IND_Y,     2, 5 ),
		new Opcode( 0x12, OpcodeMnemonic.ORA, AddressMode.ZPG_IND,   2, 5 ),
		new Opcode( 0x13, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x14, OpcodeMnemonic.TRB, AddressMode.ZPG,       2, 5 ),
		new Opcode( 0x15, OpcodeMnemonic.ORA, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0x16, OpcodeMnemonic.ASL, AddressMode.ZPG_X,     2, 6 ),
		new Opcode( 0x17, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x18, OpcodeMnemonic.CLC, AddressMode.IMP,       1, 2 ),
		new Opcode( 0x19, OpcodeMnemonic.ORA, AddressMode.ABS_Y,     3, 4 ),
		new Opcode( 0x1a, OpcodeMnemonic.INA, AddressMode.ACC,       1, 2 ),
		new Opcode( 0x1b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x1c, OpcodeMnemonic.TRB, AddressMode.ABS,       3, 6 ),
		new Opcode( 0x1d, OpcodeMnemonic.ORA, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0x1e, OpcodeMnemonic.ASL, AddressMode.ABS_X,     3, 7 ),
		new Opcode( 0x1f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x20, OpcodeMnemonic.JSR, AddressMode.ABS,       3, 6 ),
		new Opcode( 0x21, OpcodeMnemonic.AND, AddressMode.IND_X,     2, 6 ),
		new Opcode( 0x22, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 2 ),
		new Opcode( 0x23, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x24, OpcodeMnemonic.BIT, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x25, OpcodeMnemonic.AND, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x26, OpcodeMnemonic.ROL, AddressMode.ZPG,       2, 5 ),
		new Opcode( 0x27, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x28, OpcodeMnemonic.PLP, AddressMode.IMP,       1, 4 ),
		new Opcode( 0x29, OpcodeMnemonic.AND, AddressMode.IMM,       2, 2 ),
		new Opcode( 0x2a, OpcodeMnemonic.ROL, AddressMode.ACC,       1, 2 ),
		new Opcode( 0x2b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x2c, OpcodeMnemonic.BIT, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x2d, OpcodeMnemonic.AND, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x2e, OpcodeMnemonic.ROL, AddressMode.ABS,       3, 6 ),
		new Opcode( 0x2f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x30, OpcodeMnemonic.BMI, AddressMode.REL,       2, 2 ),
		new Opcode( 0x31, OpcodeMnemonic.AND, AddressMode.IND_Y,     2, 5 ),
		new Opcode( 0x32, OpcodeMnemonic.AND, AddressMode.ZPG_IND,   2, 5 ),
		new Opcode( 0x33, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x34, OpcodeMnemonic.BIT, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0x35, OpcodeMnemonic.AND, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0x36, OpcodeMnemonic.ROL, AddressMode.ZPG_X,     2, 6 ),
		new Opcode( 0x37, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x38, OpcodeMnemonic.SEC, AddressMode.IMP,       1, 2 ),
		new Opcode( 0x39, OpcodeMnemonic.AND, AddressMode.ABS_Y,     3, 4 ),
		new Opcode( 0x3a, OpcodeMnemonic.DEA, AddressMode.ACC,       1, 2 ),
		new Opcode( 0x3b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x3c, OpcodeMnemonic.BIT, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0x3d, OpcodeMnemonic.AND, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0x3e, OpcodeMnemonic.ROL, AddressMode.ABS_X,     3, 7 ),
		new Opcode( 0x3f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x40, OpcodeMnemonic.RTI, AddressMode.IMP,       1, 6 ),
		new Opcode( 0x41, OpcodeMnemonic.EOR, AddressMode.IND_X,     2, 6 ),
		new Opcode( 0x42, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 2 ),
		new Opcode( 0x43, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x44, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 3 ),
		new Opcode( 0x45, OpcodeMnemonic.EOR, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x46, OpcodeMnemonic.LSR, AddressMode.ZPG,       2, 5 ),
		new Opcode( 0x47, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x48, OpcodeMnemonic.PHA, AddressMode.IMP,       1, 3 ),
		new Opcode( 0x49, OpcodeMnemonic.EOR, AddressMode.IMM,       2, 2 ),
		new Opcode( 0x4a, OpcodeMnemonic.LSR, AddressMode.ACC,       1, 2 ),
		new Opcode( 0x4b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x4c, OpcodeMnemonic.JMP, AddressMode.ABS,       3, 3 ),
		new Opcode( 0x4d, OpcodeMnemonic.EOR, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x4e, OpcodeMnemonic.LSR, AddressMode.ABS,       3, 6 ),
		new Opcode( 0x4f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x50, OpcodeMnemonic.BVC, AddressMode.REL,       2, 2 ),
		new Opcode( 0x51, OpcodeMnemonic.EOR, AddressMode.IND_Y,     2, 5 ),
		new Opcode( 0x52, OpcodeMnemonic.EOR, AddressMode.ZPG_IND,   2, 5 ),
		new Opcode( 0x53, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x54, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 4 ),
		new Opcode( 0x55, OpcodeMnemonic.EOR, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0x56, OpcodeMnemonic.LSR, AddressMode.ZPG_X,     2, 6 ),
		new Opcode( 0x57, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x58, OpcodeMnemonic.CLI, AddressMode.IMP,       1, 2 ),
		new Opcode( 0x59, OpcodeMnemonic.EOR, AddressMode.ABS_Y,     3, 4 ),
		new Opcode( 0x5a, OpcodeMnemonic.PHY, AddressMode.IMP,       1, 3 ),
		new Opcode( 0x5b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x5c, OpcodeMnemonic.NOP, AddressMode.IMP,       3, 8 ),
		new Opcode( 0x5d, OpcodeMnemonic.EOR, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0x5e, OpcodeMnemonic.LSR, AddressMode.ABS_X,     3, 7 ),
		new Opcode( 0x5f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x60, OpcodeMnemonic.RTS, AddressMode.IMP,       1, 6 ),
		new Opcode( 0x61, OpcodeMnemonic.ADC, AddressMode.IND_X,     2, 6 ),
		new Opcode( 0x62, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 2 ),
		new Opcode( 0x63, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x64, OpcodeMnemonic.STZ, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x65, OpcodeMnemonic.ADC, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x66, OpcodeMnemonic.ROR, AddressMode.ZPG,       2, 5 ),
		new Opcode( 0x67, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x68, OpcodeMnemonic.PLA, AddressMode.IMP,       1, 4 ),
		new Opcode( 0x69, OpcodeMnemonic.ADC, AddressMode.IMM,       2, 2 ),
		new Opcode( 0x6a, OpcodeMnemonic.ROR, AddressMode.ACC,       1, 2 ),
		new Opcode( 0x6b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x6c, OpcodeMnemonic.JMP, AddressMode.ABS_IND,   3, 6 ),
		new Opcode( 0x6d, OpcodeMnemonic.ADC, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x6e, OpcodeMnemonic.ROR, AddressMode.ABS,       3, 6 ),
		new Opcode( 0x6f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x70, OpcodeMnemonic.BVS, AddressMode.REL,       2, 2 ),
		new Opcode( 0x71, OpcodeMnemonic.ADC, AddressMode.IND_Y,     2, 5 ),
		new Opcode( 0x72, OpcodeMnemonic.ADC, AddressMode.ZPG_IND,   2, 5 ),
		new Opcode( 0x73, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x74, OpcodeMnemonic.STZ, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0x75, OpcodeMnemonic.ADC, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0x76, OpcodeMnemonic.ROR, AddressMode.ZPG_X,     2, 6 ),
		new Opcode( 0x77, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x78, OpcodeMnemonic.SEI, AddressMode.IMP,       1, 2 ),
		new Opcode( 0x79, OpcodeMnemonic.ADC, AddressMode.ABS_Y,     3, 4 ),
		new Opcode( 0x7a, OpcodeMnemonic.PLY, AddressMode.IMP,       1, 4 ),
		new Opcode( 0x7b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x7c, OpcodeMnemonic.JMP, AddressMode.ABS_IND_X, 3, 6 ),
		new Opcode( 0x7d, OpcodeMnemonic.ADC, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0x7e, OpcodeMnemonic.ROR, AddressMode.ABS_X,     3, 7 ),
		new Opcode( 0x7f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x80, OpcodeMnemonic.BRA, AddressMode.REL,       2, 2 ),
		new Opcode( 0x81, OpcodeMnemonic.STA, AddressMode.IND_X,     2, 6 ),
		new Opcode( 0x82, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 2 ),
		new Opcode( 0x83, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x84, OpcodeMnemonic.STY, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x85, OpcodeMnemonic.STA, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x86, OpcodeMnemonic.STX, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0x87, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x88, OpcodeMnemonic.DEY, AddressMode.IMP,       1, 2 ),
		new Opcode( 0x89, OpcodeMnemonic.BIT, AddressMode.IMM,       2, 2 ),
		new Opcode( 0x8a, OpcodeMnemonic.TXA, AddressMode.IMP,       1, 2 ),
		new Opcode( 0x8b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x8c, OpcodeMnemonic.STY, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x8d, OpcodeMnemonic.STA, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x8e, OpcodeMnemonic.STX, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x8f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x90, OpcodeMnemonic.BCC, AddressMode.REL,       2, 2 ),
		new Opcode( 0x91, OpcodeMnemonic.STA, AddressMode.IND_Y,     2, 6 ),
		new Opcode( 0x92, OpcodeMnemonic.STA, AddressMode.ZPG_IND,   2, 5 ),
		new Opcode( 0x93, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x94, OpcodeMnemonic.STY, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0x95, OpcodeMnemonic.STA, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0x96, OpcodeMnemonic.STX, AddressMode.ZPG_Y,     2, 4 ),
		new Opcode( 0x97, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x98, OpcodeMnemonic.TYA, AddressMode.IMP,       1, 2 ),
		new Opcode( 0x99, OpcodeMnemonic.STA, AddressMode.ABS_Y,     3, 5 ),
		new Opcode( 0x9a, OpcodeMnemonic.TXS, AddressMode.IMP,       1, 2 ),
		new Opcode( 0x9b, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0x9c, OpcodeMnemonic.STZ, AddressMode.ABS,       3, 4 ),
		new Opcode( 0x9d, OpcodeMnemonic.STA, AddressMode.ABS_X,     3, 5 ),
		new Opcode( 0x9e, OpcodeMnemonic.STZ, AddressMode.ABS_X,     3, 5 ),
		new Opcode( 0x9f, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xa0, OpcodeMnemonic.LDY, AddressMode.IMM,       2, 2 ),
		new Opcode( 0xa1, OpcodeMnemonic.LDA, AddressMode.IND_X,     2, 6 ),
		new Opcode( 0xa2, OpcodeMnemonic.LDX, AddressMode.IMM,       2, 2 ),
		new Opcode( 0xa3, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xa4, OpcodeMnemonic.LDY, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0xa5, OpcodeMnemonic.LDA, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0xa6, OpcodeMnemonic.LDX, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0xa7, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xa8, OpcodeMnemonic.TAY, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xa9, OpcodeMnemonic.LDA, AddressMode.IMM,       2, 2 ),
		new Opcode( 0xaa, OpcodeMnemonic.TAX, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xab, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xac, OpcodeMnemonic.LDY, AddressMode.ABS,       3, 4 ),
		new Opcode( 0xad, OpcodeMnemonic.LDA, AddressMode.ABS,       3, 4 ),
		new Opcode( 0xae, OpcodeMnemonic.LDX, AddressMode.ABS,       3, 4 ),
		new Opcode( 0xaf, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xb0, OpcodeMnemonic.BCS, AddressMode.REL,       2, 2 ),
		new Opcode( 0xb1, OpcodeMnemonic.LDA, AddressMode.IND_Y,     2, 5 ),
		new Opcode( 0xb2, OpcodeMnemonic.LDA, AddressMode.ZPG_IND,   2, 5 ),
		new Opcode( 0xb3, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xb4, OpcodeMnemonic.LDY, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0xb5, OpcodeMnemonic.LDA, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0xb6, OpcodeMnemonic.LDX, AddressMode.ZPG_Y,     2, 4 ),
		new Opcode( 0xb7, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xb8, OpcodeMnemonic.CLV, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xb9, OpcodeMnemonic.LDA, AddressMode.ABS_Y,     3, 4 ),
		new Opcode( 0xba, OpcodeMnemonic.TSX, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xbb, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xbc, OpcodeMnemonic.LDY, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0xbd, OpcodeMnemonic.LDA, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0xbe, OpcodeMnemonic.LDX, AddressMode.ABS_Y,     3, 4 ),
		new Opcode( 0xbf, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xc0, OpcodeMnemonic.CPY, AddressMode.IMM,       2, 2 ),
		new Opcode( 0xc1, OpcodeMnemonic.CMP, AddressMode.IND_X,     2, 6 ),
		new Opcode( 0xc2, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 2 ),
		new Opcode( 0xc3, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xc4, OpcodeMnemonic.CPY, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0xc5, OpcodeMnemonic.CMP, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0xc6, OpcodeMnemonic.DEC, AddressMode.ZPG,       2, 5 ),
		new Opcode( 0xc7, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xc8, OpcodeMnemonic.INY, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xc9, OpcodeMnemonic.CMP, AddressMode.IMM,       2, 2 ),
		new Opcode( 0xca, OpcodeMnemonic.DEX, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xcb, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xcc, OpcodeMnemonic.CPY, AddressMode.ABS,       3, 4 ),
		new Opcode( 0xcd, OpcodeMnemonic.CMP, AddressMode.ABS,       3, 4 ),
		new Opcode( 0xce, OpcodeMnemonic.DEC, AddressMode.ABS,       3, 6 ),
		new Opcode( 0xcf, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xd0, OpcodeMnemonic.BNE, AddressMode.REL,       2, 2 ),
		new Opcode( 0xd1, OpcodeMnemonic.CMP, AddressMode.IND_Y,     2, 5 ),
		new Opcode( 0xd2, OpcodeMnemonic.CMP, AddressMode.ZPG_IND,   2, 5 ),
		new Opcode( 0xd3, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xd4, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 4 ),
		new Opcode( 0xd5, OpcodeMnemonic.CMP, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0xd6, OpcodeMnemonic.DEC, AddressMode.ZPG_X,     2, 6 ),
		new Opcode( 0xd7, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xd8, OpcodeMnemonic.CLD, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xd9, OpcodeMnemonic.CMP, AddressMode.ABS_Y,     3, 4 ),
		new Opcode( 0xda, OpcodeMnemonic.PHX, AddressMode.IMP,       1, 3 ),
		new Opcode( 0xdb, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xdc, OpcodeMnemonic.NOP, AddressMode.IMP,       3, 4 ),
		new Opcode( 0xdd, OpcodeMnemonic.CMP, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0xde, OpcodeMnemonic.DEC, AddressMode.ABS_X,     3, 7 ),
		new Opcode( 0xdf, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xe0, OpcodeMnemonic.CPX, AddressMode.IMM,       2, 2 ),
		new Opcode( 0xe1, OpcodeMnemonic.SBC, AddressMode.IND_X,     2, 6 ),
		new Opcode( 0xe2, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 2 ),
		new Opcode( 0xe3, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xe4, OpcodeMnemonic.CPX, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0xe5, OpcodeMnemonic.SBC, AddressMode.ZPG,       2, 3 ),
		new Opcode( 0xe6, OpcodeMnemonic.INC, AddressMode.ZPG,       2, 5 ),
		new Opcode( 0xe7, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xe8, OpcodeMnemonic.INX, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xe9, OpcodeMnemonic.SBC, AddressMode.IMM,       2, 2 ),
		new Opcode( 0xea, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xeb, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xec, OpcodeMnemonic.CPX, AddressMode.ABS,       3, 4 ),
		new Opcode( 0xed, OpcodeMnemonic.SBC, AddressMode.ABS,       3, 4 ),
		new Opcode( 0xee, OpcodeMnemonic.INC, AddressMode.ABS,       3, 6 ),
		new Opcode( 0xef, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xf0, OpcodeMnemonic.BEQ, AddressMode.REL,       2, 2 ),
		new Opcode( 0xf1, OpcodeMnemonic.SBC, AddressMode.IND_Y,     2, 5 ),
		new Opcode( 0xf2, OpcodeMnemonic.SBC, AddressMode.ZPG_IND,   2, 5 ),
		new Opcode( 0xf3, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xf4, OpcodeMnemonic.NOP, AddressMode.IMP,       2, 4 ),
		new Opcode( 0xf5, OpcodeMnemonic.SBC, AddressMode.ZPG_X,     2, 4 ),
		new Opcode( 0xf6, OpcodeMnemonic.INC, AddressMode.ZPG_X,     2, 6 ),
		new Opcode( 0xf7, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xf8, OpcodeMnemonic.SED, AddressMode.IMP,       1, 2 ),
		new Opcode( 0xf9, OpcodeMnemonic.SBC, AddressMode.ABS_Y,     3, 4 ),
		new Opcode( 0xfa, OpcodeMnemonic.PLX, AddressMode.IMP,       1, 4 ),
		new Opcode( 0xfb, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 ),
		new Opcode( 0xfc, OpcodeMnemonic.NOP, AddressMode.IMP,       3, 4 ),
		new Opcode( 0xfd, OpcodeMnemonic.SBC, AddressMode.ABS_X,     3, 4 ),
		new Opcode( 0xfe, OpcodeMnemonic.INC, AddressMode.ABS_X,     3, 7 ),
		new Opcode( 0xff, OpcodeMnemonic.NOP, AddressMode.IMP,       1, 1 )
	};
	
	public enum OpcodeMnemonic
	{
		ADC, AND, ASL, BCC, BCS, BEQ,
		BIT, BMI, BNE, BPL, BRA, BRK,
		BVC, BVS, CLC, CLD, CLI, CLV,
		CMP, CPX, CPY, DEA, DEC, DEX,
		DEY, EOR, INA, INC, INX, INY,
		JMP, JSR, LDA, LDX, LDY, LSR,
		NOP, ORA, PHA, PHP, PHX, PHY,
		PLA, PLP, PLX, PLY, ROL, ROR,
		RTI, RTS, SBC, SEC, SED, SEI,
		STA, STX, STY, STZ, TAX, TAY,
		TRB, TSB, TSX, TXA, TXS, TYA,

		// Pending interrupts
		IRQ,  // Interrupt request (made by hardware)
		NMI,  // Non-maskable interrupt (hardware failure)
		RES,  // Reset interrupt (ctrl-reset / boot handler)
		HLT,  // Halt CPU (non-standard - called by host application)
	};

	public enum AddressMode
	{
		IMM,           ABS,           ZPG,
		ACC,           IMP,           IND_X,
		IND_Y,         ZPG_X,         ZPG_Y,
		ABS_X,         ABS_Y,         REL,
		ABS_IND,       ABS_IND_X,     ZPG_IND;
		
		private static final String ADDRESS_MODE_NAME[] =
		{
			"IMM         ", "ABS         ", "ZPG         ",
			"ACC         ", "IMP         ", "(IND, X)    ",
			"(IND), Y    ", "ZPG, X      ", "ZPG, Y      ",
			"ABS, X      ", "ABS, Y      ", "REL         ",
			"(ABS)       ", "ABS (IND, X)", "(ZPG)       "
		};

		public String toString() {
			return ADDRESS_MODE_NAME[this.ordinal()];
		}
		
	};

	private int popStack()
	{
		/// TODO: Double check that underflow is ignored in real CPU ///
		int val = reg.getS()+1;
		reg.setS(val);
		return memory.getByte( STACK_PAGE | val );
	}

	private void pushStack( int value )
	{
		/// TODO: Double check that overflow is ignored in real CPU ///
		int val = reg.getS();
		memory.setByte((STACK_PAGE | val), value);
		reg.setS(val-1);
	}

	private void branchTest( boolean condition ) {

		if( condition ) {
			int value = memory.getByte(operandPtr);
			int oldPage = newPc>>8;
			newPc += (byte) value;
			newPc &= 0xffff;
			cycleCount++;
			if( oldPage != newPc>>8 )
				cycleCount++;
		}

	}

	private void ptrAdd( int i, boolean addPageCrossCycle ) {
		int oldPage = operandPtr>>8;
		operandPtr += i;
		operandPtr &= 0xffff;
		if( addPageCrossCycle && operandPtr>>8 != oldPage )
			cycleCount++;
	}

	private boolean addsPageCrossCycleForAbsIndexedRead(Opcode opcode, AddressMode mode) {
		if( opcode==null || opcode.getMnemonic()==null )
			return false;
		switch( opcode.getMnemonic() ) {
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

	public Cpu65c02( MemoryBusIIe memory, long unitsPerCycle ) {
		super(unitsPerCycle);
		this.memory = memory;
		this.executionPlanner = new CpuExecutionPlanner(memory, reg);
	}

	protected Opcode[] getOpcodeTable() {
		return OPCODE;
	}

	@Override
	public void coldReset() throws HardwareException {
		
		// Startup register values
		/// Verify values for real CPU ///
		reg.setA(0xff);
		reg.setY(0xff);
		reg.setX(0xff);
		reg.setPC(0xff);
		// 65C02 reset sequence internally performs three stack-pointer adjustments.
		// Initialize S to 0x00 so RES adjustment lands at 0xFD (MAME/hardware behavior).
		reg.setS(0x00);
		reg.setP(0xff);
		newPc = 0xffff;

		// This variable is used to suspend CPU access to memory by hardware
		idleCycle = 0;

		// First instruction is a reset interrupt
		opcode = null;
		interruptPending = null;
		newOpcode = INTERRUPT_RES;
		cycleCount = INTERRUPT_RES.getCycleTime();
		resetPOverride = null;
		resetAOverride = null;
		resetXOverride = null;
		resetYOverride = null;
		resetSOverride = null;
		pendingInstructionCyclesConsumed = 0;
		totalCycleCount = 0L;
		executionQueue.clear();
		enqueueNextInstructionEvents();
		verifyExecutionQueueInvariant("cold_reset");

		memory.coldReset();
		
	}

	@Override
	public void cycle() throws HardwareException {
		if( idleCycle>5 )
			throw new HardwareException("Hardware has requested an extended delay, comprimising CPU data integrity");
		if( executionQueue.isEmpty() )
			enqueueNextInstructionEvents();
		CpuExecutionEvent event = executionQueue.removeFirst();
		event.action.run();
		verifyExecutionQueueInvariant("post_cycle");
	}

	private void enqueueNextInstructionEvents() {
		Opcode scheduledOpcode = newOpcode;
		int scheduledPc = newPc & 0xffff;
		CpuExecutionPlanner.Plan plan = executionPlanner.buildPlan(scheduledOpcode, scheduledPc);
		pendingInstructionCyclesConsumed = plan.pendingCycles;
		for( int i = 0; i<plan.pendingCycles; i++ )
			enqueueExecutionEvent(false, this::runPendingInstructionCycle);
		enqueueExecutionEvent(true, this::executeInstructionAtomicCore);
		verifyExecutionQueueInvariant("enqueue_instruction");
	}

	private void enqueueExecutionEvent(boolean instructionEnd, CpuExecutionAction action) {
		executionQueue.addLast(new CpuExecutionEvent(instructionEnd, action));
	}

	private void runPendingInstructionCycle() {
		incSleepCycles(1);
	}

	private void verifyExecutionQueueInvariant(String stage) {
		if( executionQueue.isEmpty() )
			throw new IllegalStateException("Execution queue invariant failed at "+stage+": queue must not be empty");
		int instructionEndCount = 0;
		int index = 0;
		for( CpuExecutionEvent event : executionQueue ) {
			if( event.instructionEnd ) {
				instructionEndCount++;
				if( index!=executionQueue.size()-1 )
					throw new IllegalStateException("Execution queue invariant failed at "+stage+": instructionEnd must be tail event");
			}
			index++;
		}
		if( instructionEndCount!=1 )
			throw new IllegalStateException("Execution queue invariant failed at "+stage+": expected exactly one instructionEnd event");
	}

	private void executeInstructionAtomicCore() throws HardwareException {
	
		/// TODO: Sather 4-27 and C-15 contains more information on per-cycle instruction effects and double / triple / quadruple strobe effects
		if( idleCycle>5 )
			throw new HardwareException("Hardware has requested an extended delay, comprimising CPU data integrity");

		opcode = newOpcode;
		reg.setPC(newPc);
		cycleCount = opcode.getCycleTime();

		// Check whether current instruction is complete before applying changes
		// TODO: move this and other proven variables inside the switch
		int operandCounter = reg.getPC()+1;
		
		// Expected next instruction position
		newPc = reg.getPC() + opcode.getInstrSize();
	
		/// TEST:
		///  Dereferenced addresses on page zero forces both low and high bytes to be pulled from page zero, even if the page boundary is crossed
		///  According to the NCR 65C02 Specs, all zero-page indirect opcodes use this convention except "zero-page indirect addressing" itself
		///  It would seem to follow that zero-page indirect addressing would also follow this convention
	
		/// TODO: These take one additional clock if the effective address page and given address page are different ///
	
		// Find these values by their addressing mode
		switch( opcode.getAddressMode() ) {
	
			case IMM:
				// Immediate
				// Literal value
				// +1 byte
				operandPtr = operandCounter;
				break;
	
			case ABS:
				// Absolute
				// ADL:ADH
				// +2 byte
				operandPtr = memory.getWord16LittleEndian(operandCounter);
				break;
	
			case ZPG:
				// Zero-page
				// $00:ADL
				// +1 byte
				operandPtr = memory.getByte(operandCounter);
				break;
	
			case ACC:
				// Accumulator
				// A
				// +0 bytes
				break;
	
			case IMP:
				// Implied
				// No address
				// PC increment varies for NOP's
				break;
			
			case IND_X:
				// (Indirect, X)
				// ($00:[ADL+X]) ñ carries discarded for low and high addr bytes
				// +1 byte
				operandPtr = (memory.getByte(operandCounter) + reg.getX())&0xff;
				operandPtr = memory.getWord16LittleEndian(operandPtr, 0xff);
				break;
	
			case IND_Y:
				// (Indirect), Y
				// (ADH:ADL)+Y
				// +1 byte
				operandPtr = memory.getByte(operandCounter);
				operandPtr = memory.getWord16LittleEndian(operandPtr, 0xff);
				int basePtr = operandPtr;
				operandPtr += reg.getY();
				operandPtr &= 0xffff;
				// 65C02 adds one cycle for (indirect),Y reads on page cross.
				if( (basePtr>>8)!=(operandPtr>>8) && opcode.getMnemonic()!=OpcodeMnemonic.STA )
					cycleCount++;
				break;
	
			case ZPG_X:
				// Zero-page, X
				// $00:[ADL+X]
				// +1 byte
				operandPtr = ( memory.getByte(operandCounter) + reg.getX() )&0xff;
				break;
	
			case ZPG_Y:
				// Zero-page, Y
				// $00:[ADL+Y]
				// +1 byte
				operandPtr = ( memory.getByte(operandCounter) + reg.getY() )&0xff;
				break;
			
			case ABS_X:
				// Absolute, X
				// [ADL:ADH] + X
				// +2 byte
				operandPtr = memory.getWord16LittleEndian(operandCounter);
				ptrAdd(reg.getX(), addsPageCrossCycleForAbsIndexedRead(opcode, AddressMode.ABS_X));
				break;

			case ABS_Y:
				// Absolute, Y
				// [ADL:ADH] + Y
				// +2 byte
				operandPtr = memory.getWord16LittleEndian(operandCounter);
				ptrAdd(reg.getY(), addsPageCrossCycleForAbsIndexedRead(opcode, AddressMode.ABS_Y));
				break;
			
			case REL:
				// Relative
				// PCL:PCH + ADL
				// +1 byte
				operandPtr = operandCounter;
				break;
	
			case ABS_IND:
				// (Absolute)
				// PCL:PCH = (ADL:ADH)
				// +2 byte
				operandPtr = memory.getWord16LittleEndian(operandCounter);
				operandPtr = memory.getWord16LittleEndian(operandPtr);
				break;
	
			case ABS_IND_X:
				// Absolute (indirect, X)
				// PCL:PCH = (ADL:ADH + X)
				// +2 byte
				operandPtr = memory.getWord16LittleEndian(operandCounter);
				ptrAdd(reg.getX(), false);
				operandPtr = memory.getWord16LittleEndian(operandPtr);
				break;
	
			case ZPG_IND:
				// (Zero-page)
				// ($00:ADL)
				// +1 byte
				operandPtr = memory.getByte(operandCounter);
				operandPtr = memory.getWord16LittleEndian(operandPtr, 0xff);
				break;
	
		};

		int value;
		switch( opcode.getMnemonic() ) {
		
			case ADC:
				// Add 1 cycle for decimal mode
				// A + M + C . A
				value = memory.getByte(operandPtr);
				if( reg.getP(StatusRegister.D) ) {
					cycleCount++;
					applyAdcDecimal(reg, value);
				}
				else {
					int regA = reg.getA();
					int valAdd = value;
					value = regA + valAdd;
					if( reg.getP(StatusRegister.C) )
						value++;
					reg.setA(value);
					reg.testPCZN(value);
					reg.testP(((regA^value)&(valAdd^value)&0x80)!=0, StatusRegister.V);
				}
				break;
	
			case AND:
				// A & M . A
				value = memory.getByte(operandPtr);
				value &= reg.getA();
				reg.setA(value);
				reg.testPZN(value);
				break;
	
			case ASL:
				if( opcode.getAddressMode() == AddressMode.ACC ) {
					// C << A << 0
					value = reg.getA()<<1;
					reg.setA(value);
					reg.testPCZN(value);
				}
				else {
					// C << M << 0
					value = memory.getByte(operandPtr)<<1;
					memory.setByte(operandPtr, value);
					reg.testPCZN(value);
				}
				break;
	
			case BCC:
				branchTest( !reg.getP(StatusRegister.C) );
				break;
	
			case BCS:
				branchTest( reg.getP(StatusRegister.C) );
				break;
	
			case BEQ:
				branchTest( reg.getP(StatusRegister.Z) );
				break;
	
			case BIT:
				// A & M
				// Stores memory bits 6 and 7 in bits 6 and 7 of the processor register
				// If immediate addressing mode is used, BIT does not effect bit 6 or 7 (4-22 Sather)
				value = memory.getByte(operandPtr);
				if( opcode.getAddressMode() != AddressMode.IMM ) 
					reg.setP(reg.getP() & 0x3f | (value&0xc0));
				reg.testPZ(reg.getA() & value);
				break;
	
			case BMI:
				branchTest( reg.getP(StatusRegister.N) );
				break;
	
			case BNE:
				branchTest( !reg.getP(StatusRegister.Z) );
				break;
	
			case BPL:
				branchTest( !reg.getP(StatusRegister.N) );
				break;
	
			case BRA:
				branchTest( true );
				break;
	
			case BRK:
				pushStack(newPc>>8);
				pushStack(newPc);
				pushStack(reg.getP() | StatusRegister.B.value);
				reg.setP(StatusRegister.I);    // Set interrupt disable
				reg.clearP(StatusRegister.D);  // Clear decimal flag
				newPc = memory.getWord16LittleEndian(INT_BRK_VECTOR_ADDR);
				break;
	
			case BVC:
				branchTest( !(reg.getP(StatusRegister.V)) );
				break;
	
			case BVS:
				branchTest( reg.getP(StatusRegister.V) );
				break;
	
			case CLC:
				// 0 . P.C;
				reg.clearP(StatusRegister.C);
				break;
	
			case CLD:
				// 0 . P.D;
				reg.clearP(StatusRegister.D);
				break;
	
			case CLI:
				// 0 . P.I;
				reg.clearP(StatusRegister.I);
				break;
	
			case CLV:
				// 0 . P.V;
				reg.clearP(StatusRegister.V);
				break;
	
			case CMP:
				// A - M
				{
					value = memory.getByte(operandPtr);
					int regA = reg.getA();
					int valAdd = value^0xff;
					value = regA + valAdd;
					value++;
					reg.testPCZN(value);
				}
				break;
			
			case CPX:
				// X - M
				reg.testPC_ZN(reg.getX() - memory.getByte(operandPtr));
				{
					value = memory.getByte(operandPtr);
					int regX = reg.getX();
					int valAdd = value^0xff;
					value = regX + valAdd;
					value++;
					reg.testPCZN(value);
				}
				break;
	
			case CPY:
				// Y - M
				{
					value = memory.getByte(operandPtr);
					int regY = reg.getY();
					int valAdd = value^0xff;
					value = regY + valAdd;
					value++;
					reg.testPCZN(value);
				}
				break;
	
			case DEA:
				// A - 1 . A
				value = reg.getA() - 1;
				reg.setA(value);
				reg.testPZN(value);
				break;
			
			case DEC:
				// M - 1 . M
				value = memory.getByte(operandPtr) - 1;
				memory.setByte(operandPtr, value);
				reg.testPZN(value);
				break;
	
			case DEX:
				// X - 1 . X
				value = reg.getX() - 1;
				reg.setX(value);
				reg.testPZN(value);
				break;
	
			case DEY:
				// Y - 1 . Y
				value = reg.getY() - 1;
				reg.setY(value);
				reg.testPZN(value);
				break;
			
			case EOR:
				// A ^ M . A
				value = reg.getA() ^ memory.getByte(operandPtr);
				reg.setA(value);
				reg.testPZN(value);
				break;
			
			case INA:
				// A + 1 . A
				value = reg.getA() + 1;
				reg.setA(value);
				reg.testPZN(value);
				break;
	
			case INC:
				// M + 1 . M
				value = memory.getByte(operandPtr) + 1;
				memory.setByte(operandPtr, value);
				reg.testPZN(value);
				break;
	
			case INX:
				// X + 1 . X
				value = reg.getX() + 1;
				reg.setX(value);
				reg.testPZN(value);
				break;
	
			case INY:
				// Y + 1 . Y
				value = reg.getY() + 1;
				reg.setY(value);
				reg.testPZN(value);
				break;
	
			case JMP:
				// M . PC
				newPc = operandPtr;
				break;
	
			case JSR:
				// PC-1 . (S)
				// M . PC
				newPc--;
				pushStack(((newPc)>>8));
				pushStack(newPc);
				newPc = operandPtr;
				break;

			case LDA:
				// M . A
				value = memory.getByte(operandPtr);
				reg.setA(value);
				reg.testPZN(value);
				break;
	
			case LDX:
				// M . X
				value = memory.getByte(operandPtr);
				reg.setX(value);
				reg.testPZN(value);
				break;
	
			case LDY:
				// M . Y
				value = memory.getByte(operandPtr);
				reg.setY(value);
				reg.testPZN(value);
				break;
	
			case LSR:
				if( opcode.getAddressMode() == AddressMode.ACC ) {
					// 0 >> A >> C
					value = reg.getA();
					reg.testP((value & 0x01)!=0, StatusRegister.C);
					value >>= 1;
					reg.setA(value);
					reg.clearP(StatusRegister.N);
					reg.testPZ(value);
				}
				else {
					// 0 >> M >> C
					value = memory.getByte(operandPtr);
					reg.testP((value & 0x01)!=0, StatusRegister.C);
					value >>= 1;
					memory.setByte(operandPtr, value);
					reg.clearP(StatusRegister.N);
					reg.testPZ(value);
				}
				break;
	
			case NOP:
				// No operation
				break;

			case ORA:
				// A | M . A
				value = memory.getByte(operandPtr) | reg.getA();
				reg.setA(value);
				reg.testPZN(value);
				break;
	
			case PHA:
				// A . (S)
				pushStack(reg.getA());
				break;
	
			case PHP:
				// P . (S)
				pushStack(reg.getP());
				break;
	
			case PHX:
				// X . (S)
				pushStack(reg.getX());
				break;
	
			case PHY:
				// Y . (S)
				pushStack(reg.getY());
				break;
	
			case PLA:
				// (S) . A
				value = popStack();
				reg.setA(value);
				reg.testPZN(value);
				break;
	
			case PLP:
				// (S) . P
				reg.setP(popStack()|StatusRegister.B.value);
				break;
	
			case PLX:
				// (S) . X
				value = popStack();
				reg.setX(value);
				reg.testPZN(value);
				break;
	
			case PLY:
				// (S) . Y
				value = popStack();
				reg.setY(value);
				reg.testPZN(value);
				break;

			case ROL:
				if( opcode.getAddressMode() == AddressMode.ACC ) {
					// C << A << C
					value = reg.getA()<<1;
					if( reg.getP(StatusRegister.C) )
						value |= 1;
					reg.setA(value);
					reg.testPCZN(value);
				}
				else {
					// C << M << C
					value = memory.getByte(operandPtr)<<1;
					if( reg.getP(StatusRegister.C) )
						value |= 1;
					memory.setByte(operandPtr, value);
					reg.testPCZN(value);
				}
				break;
	
			case ROR:
				if( opcode.getAddressMode() == AddressMode.ACC ) {
					// C >> A >> C
					value = reg.getA();
					if( reg.getP(StatusRegister.C) )
						value |= 0x100;
					reg.testP((value&0x01)!=0, StatusRegister.C);
					value >>= 1;
					reg.setA(value);
					reg.testPZN(value);
				}
				else {
					// C >> M >> C
					value = memory.getByte(operandPtr);
					if( reg.getP(StatusRegister.C) )
						value |= 0x100;
					reg.testP((value&0x01)!=0, StatusRegister.C);
					value >>= 1;
					memory.setByte(operandPtr, value);
					reg.testPZN(value);
				}
				break;

			case RTI:
				// (S) . PC
				reg.setP(popStack() | StatusRegister.B.value);
				newPc = popStack();
				newPc |= popStack() << 8;
				break;
	
			case RTS:
				// (S)+1 . PC
				newPc = popStack();
				newPc |= popStack() << 8;
				newPc++;
				newPc &= 0xffff;
				break;
	
			case SBC:
				value = memory.getByte(operandPtr);
				if( reg.getP(StatusRegister.D) ) {
					// A - M - !C . A
					cycleCount++;
					applySbcDecimal(reg, value);
				}
				else {
					int regA = reg.getA();
					int valAdd = value^0xff;
					value = regA + valAdd;
					if( reg.getP(StatusRegister.C) )
						value++;
					reg.setA(value);
					reg.testPCZN(value);
					reg.testP(((regA^value)&(valAdd^value)&0x80)!=0, StatusRegister.V);
				}
				break;

			case SEC:
				// 1 . P.C;
				reg.setP(StatusRegister.C);
				break;
	
			case SED:
				// 1 . P.D;
				reg.setP(StatusRegister.D);
				break;
	
			case SEI:
				// 1 . P.I;
				reg.setP(StatusRegister.I);
				break;
	
			case STA:
				// A . M
				memory.setByte(operandPtr, reg.getA());
				break;
	
			case STX:
				// X . M
				memory.setByte(operandPtr, reg.getX());
				break;
	
			case STY:
				// Y . M
				memory.setByte(operandPtr, reg.getY());
				break;
	
			case STZ:
				// $00 . M
				memory.setByte(operandPtr, 0x00);
				break;

			case TAX:
				// A . X
				value = reg.getA();
				reg.setX(value);
				reg.testPZN(value);
				break;
	
			case TAY:
				// A . Y
				value = reg.getA();
				reg.setY(value);
				reg.testPZN(value);
				break;
	
			case TRB:
				// A & M . M
				value = memory.getByte(operandPtr);
				reg.testPZ(reg.getA() & value);
				value &= ~reg.getA();
				memory.setByte(operandPtr, value);
				break;
	
			case TSB:
				// A | M . M
				value = memory.getByte(operandPtr);
				reg.testPZ(reg.getA() & value);
				value |= reg.getA();
				memory.setByte(operandPtr, value);
				break;
	
			case TSX:
				// S . X
				value = reg.getS();
				reg.setX(value);
				reg.testPZN(value);
				break;
	
			case TXA:
				// X . A
				value = reg.getX();
				reg.setA(value);
				reg.testPZN(value);
				break;
	
			case TXS:
				// X . S
				reg.setS(reg.getX());
				break;
	
			case TYA:
				// Y . A
				value = reg.getY();
				reg.setA(value);
				reg.testPZN(value);
				break;
	
			case IRQ:
				// IRQ's are given priority starting with slot 1 to 7 (4-16 of Sather)
				pushStack(newPc>>8);
				pushStack(newPc);
				pushStack(reg.getP()&~StatusRegister.B.value);
				reg.setP(StatusRegister.I);    // Set interrupt disable
				reg.clearP(StatusRegister.D);  // Clear decimal flag
				newPc = memory.getWord16LittleEndian(INT_IRQ_VECTOR_ADDR);
				break;
	
			case NMI:
				pushStack(newPc>>8);
				pushStack(newPc);
				pushStack(reg.getP()&~StatusRegister.B.value);
				reg.setP(StatusRegister.I);    // Set interrupt disable
				reg.clearP(StatusRegister.D);  // Clear decimal flag
				newPc = memory.getWord16LittleEndian(INT_NMI_VECTOR_ADDR);
				break;
	
			case RES:
				// Reset - reset key up / cold switch reset
				// This sequence lasts 6 cycles (NCR 65C02 Datasheet)
				// Also pulls 3 values from the stack and resets all but 2 switches in the MMU
				/// Should emulate reset signature recognition described in 4-14 and 5-29 of Sather ///
				// Reset adjusts S as if three pulls occurred, but does not write stack memory.
				reg.setS(reg.getS()-3);
				reg.setP(StatusRegister.I);    // Set interrupt disable
				reg.clearP(StatusRegister.D);  // Clear decimal flag
				if( resetAOverride!=null ) {
					reg.setA(resetAOverride);
				}
				if( resetXOverride!=null ) {
					reg.setX(resetXOverride);
				}
				if( resetYOverride!=null ) {
					reg.setY(resetYOverride);
				}
				if( resetSOverride!=null ) {
					reg.setS(resetSOverride);
				}
				if( resetPOverride!=null ) {
					reg.setP(resetPOverride);
				}
				newPc = memory.getWord16LittleEndian(INT_RES_VECTOR_ADDR);
				isHalted = false;
				break;
	
			case HLT:
				// Halt execution (reset key down)
				// _TEXT and _MIXED statuses are not modified by a reset interrupt
				// Sather 7-3, Sather I-5 suggests the Apple II reset operates differently than the Apple IIe
				if( !isHalted ) {
					memory.warmReset();
					isHalted = true;
				}
				break;

			default:
				throw new RuntimeException("[Opcode "+opcode.getMnemonic().toString()+" not yet supported]");
				//throw new RuntimeException("Command "+opcode.getMnemonic().toString()+" not supported");
				//break;
				
		}
	
		lastInstructionCycleCount = cycleCount;
		lastCycleCount = idleCycle+cycleCount;
		totalCycleCount += cycleCount;
		int cyclesRemaining = cycleCount-pendingInstructionCyclesConsumed;
		if( cyclesRemaining<1 )
			cyclesRemaining = 1;
		incSleepCycles(idleCycle+cyclesRemaining);
		idleCycle = 0;
		pendingInstructionCyclesConsumed = 0;
	
		// Supress maskable interrupts if P.I is set
		if( interruptPending==INTERRUPT_IRQ && reg.getP(StatusRegister.I) )
			interruptPending = null;

		// Get next instruction / interrupt call
		if( interruptPending==null )
		{
			// No interrupt pending - get next memory instruction
			newOpcode = getOpcodeTable()[memory.getByte(newPc&0xffff)];
		} else {
			newOpcode = interruptPending;
			// Pending interrupt can only be changed by host
			// e.g. creating reset interrupt or restoring null state
			if( interruptPending!=INTERRUPT_HLT )
				interruptPending = null;
		}
		enqueueNextInstructionEvents();
		
	}

	static void applyAdcDecimal(Register reg, int value) {
		int a = reg.getA() & 0xff;
		int val = value & 0xff;
		int carryIn = reg.getP(StatusRegister.C) ? 1 : 0;
		int p = reg.getP() & 0xff;
		p &= ~(StatusRegister.N.value | StatusRegister.V.value | StatusRegister.Z.value | StatusRegister.C.value);

		int al = (a & 0x0f) + (val & 0x0f) + carryIn;
		if( al>9 )
			al += 6;
		int ah = (a >> 4) + (val >> 4) + (al>0x0f ? 1 : 0);

		if( ((a + val + carryIn) & 0xff)==0 )
			p |= StatusRegister.Z.value;
		else if( (ah & 0x08)!=0 )
			p |= StatusRegister.N.value;
		if( ((~(a ^ val)) & (a ^ (ah << 4)) & 0x80)!=0 )
			p |= StatusRegister.V.value;

		if( ah>9 )
			ah += 6;
		if( ah>15 )
			p |= StatusRegister.C.value;

		reg.setA(((ah << 4) | (al & 0x0f)) & 0xff);
		reg.setP(p);
	}

	static void applySbcDecimal(Register reg, int value) {
		int a = reg.getA() & 0xff;
		int val = value & 0xff;
		int borrowIn = reg.getP(StatusRegister.C) ? 0 : 1;
		int p = reg.getP() & 0xff;
		p &= ~(StatusRegister.N.value | StatusRegister.V.value | StatusRegister.Z.value | StatusRegister.C.value);

		int diff = a - val - borrowIn;
		int al = (a & 0x0f) - (val & 0x0f) - borrowIn;
		if( al<0 )
			al -= 6;
		int ah = (a >> 4) - (val >> 4) - (al<0 ? 1 : 0);

		if( (diff & 0xff)==0 )
			p |= StatusRegister.Z.value;
		else if( (diff & 0x80)!=0 )
			p |= StatusRegister.N.value;
		if( ((a ^ val) & (a ^ diff) & 0x80)!=0 )
			p |= StatusRegister.V.value;
		if( (diff & 0xff00)==0 )
			p |= StatusRegister.C.value;
		if( ah<0 )
			ah -= 6;

		reg.setA(((ah << 4) | (al & 0x0f)) & 0xff);
		reg.setP(p);
	}

	public void cycleSteal( int cycles )
	{
		if( cycles<=0 || cycles>5 )
			throw new RuntimeException("Cycle steal count must be between 1 and 5 for hardware that requires exclusive memory access");
		idleCycle = cycles;
	}
		
	public static String getHexString( long value, int maxSize ) {
		String valueStr = Long.toHexString(value).toUpperCase();
		return "0000000000000000".substring(0, Math.max(maxSize, valueStr.length())-valueStr.length())+valueStr;
	}
	
	public String getOpcodeString()
	{
		if( opcode.getMachineCode()==null )
			return "                 "+opcode.getMnemonic()+"                  ";
		return getOpcodeString(reg.getPC(), opcode);
	}

	public String getOpcodeString(Integer address, Opcode interrupt)
	{

		int mem [] = new int[3];
		if( address<0xc100 )
			for( int i = 0; i<3; i++ )
				mem[i] = memory.getMemory().getByte((address+i)&0xffff);
		else
			for( int i = 0; i<3; i++ )
				mem[i] = memory.getByte((address+i)&0xffff);
		
		Opcode op = address==null ? interrupt:getOpcodeTable()[mem[0]];
		StringBuffer out = new StringBuffer();

		Integer machineCode = op.getMachineCode();
		int operandLow = 0;
		int operandHigh = 0;
		
		if( machineCode!=null ) {
			out.append(getHexString(address, 4)+": ");
			out.append(getHexString(machineCode, 2));
		}
		else
			out.append("IRQ:    ");
		
		if( op.getInstrSize() == 0 || op.getInstrSize() == 1 )
			out.append("       ");
		else if( op.getInstrSize() == 2 ) {
			operandLow = mem[1];
			out.append(" "+getHexString(operandLow, 2)+"    ");
		}
		else if( op.getInstrSize() == 3 ) {
			operandLow = mem[1];
			operandHigh = mem[2];
			out.append(" "+getHexString(operandLow, 2)+" "+getHexString(operandHigh, 2)+" ");
		}
		else
			throw new RuntimeException("Unknown op instruction size");

		out.append("  "+op.getMnemonic()+" "+op.getAddressMode());

		if( op.getInstrSize()==2 )
			out.append("   "+getHexString(operandLow, 2));
		else if( op.getInstrSize()==3 )
			out.append(" "+getHexString(operandLow | ( operandHigh << 8), 4));
		else
			out.append("     ");

		return out.toString();
		
	/*
		switch( op.getAddressMode() ) {
		
			case IMM
				// Immediate
				break;
			
			case ABS:
				// Absolute
				break;
			
			case ZPG:
				// Zero-page
				break;
			
			case ACC:
				// Accumulator
				break;
			
			case IMP:
				// Implied
				break;
			
			case IND_X:
				// (Indirect, X)
				break;

			case IND_Y:
				// (Indirect), Y
				break;
			
			case ZPG_X:
				// Zero-page, X
				break;
			
			case ZPG_Y:
				// Zero-page, Y
				break;
			
			case ABS_X:
				// Absolute, X
				break;
			
			case ABS_Y:
				// Absolute, Y
				break;
			
			case REL:
				// Relative
				break;
			
			case ABS_IND:
				// (Absolute)
				break;
			
			case ABS_IND_X:
				// Absolute (indirect, X)
				break;
			
			case ZPG_IND:
				// (Zero-page)
				break;

		};*/
			
	}

	public Opcode getInterruptPending() {
		return interruptPending;
	}

	public void setInterruptPending(Opcode interruptPending) {
		this.interruptPending = interruptPending;
	}

	public Register getRegister() {
		return reg;
	}

	public MemoryBus8 getMemoryBus() {
		return memory;
	}

	public Opcode getOpcode() {
		return opcode;
	}

	public Opcode getPendingOpcode() {
		return newOpcode;
	}

	public int getPendingPC() {
		return newPc;
	}

	public void setResetPOverride(Integer p) {
		resetPOverride = p==null ? null : ((p|0x30)&0xff);
	}

	public void setResetAOverride(Integer a) {
		resetAOverride = a==null ? null : (a&0xff);
	}

	public void setResetXOverride(Integer x) {
		resetXOverride = x==null ? null : (x&0xff);
	}

	public void setResetYOverride(Integer y) {
		resetYOverride = y==null ? null : (y&0xff);
	}

	public void setResetSOverride(Integer s) {
		resetSOverride = s==null ? null : (s&0xff);
	}

	public int getPendingExecutionEventCount() {
		return executionQueue.size();
	}

	public boolean hasPendingInstructionEndEvent() {
		CpuExecutionEvent event = executionQueue.peekFirst();
		return event!=null && event.instructionEnd;
	}

	public boolean hasPendingInFlightMicroEvent() {
		CpuExecutionEvent event = executionQueue.peekFirst();
		return event!=null && !event.instructionEnd;
	}

	public int getLastCycleCount() {
		return lastCycleCount;
	}

	public int getLastInstructionCycleCount() {
		return lastInstructionCycleCount;
	}

	public long getTotalCycleCount() {
		return totalCycleCount;
	}

	public boolean isInstructionEndCyclePending() {
		CpuExecutionEvent event = executionQueue.peekFirst();
		return event!=null && event.instructionEnd;
	}

}
