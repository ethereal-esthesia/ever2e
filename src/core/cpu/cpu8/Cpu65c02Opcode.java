package core.cpu.cpu8;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;

import core.cpu.cpu8.Cpu65c02Microcode.AccessType;
import core.cpu.cpu8.Cpu65c02Microcode.MicroOp;

/**
 * Enum-backed opcode definitions for explicit microcoded op families.
 */
public enum Cpu65c02Opcode {

	LDA_IMM(0xA9, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	LDA_ZPG(0xA5, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	LDA_ZPG_X(0xB5, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	LDA_ABS(0xAD, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	LDA_ABS_X(0xBD, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	LDA_ABS_Y(0xB9, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	LDA_IND_X(0xA1, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),
	LDA_IND_Y(0xB1, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	LDA_IND(0xB2, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),

	STA_ZPG(0x85, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_WRITE_EA))),
	STA_ZPG_X(0x95, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_WRITE_EA))),
	STA_ABS(0x8D, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_WRITE_EA))),
	STA_ABS_X(0x9D, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_WRITE_EA))),
	STA_ABS_Y(0x99, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_WRITE_EA))),
	STA_IND_X(0x81, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_WRITE_EA))),
	STA_IND_Y(0x91, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_DUMMY, MicroOp.M_WRITE_EA))),
	STA_IND(0x92, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_WRITE_EA))),

	INC_ZPG(0xE6, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	INC_ZPG_X(0xF6, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	INC_ABS(0xEE, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	INC_ABS_X(0xFE, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),

	DEC_ZPG(0xC6, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	DEC_ZPG_X(0xD6, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	DEC_ABS(0xCE, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	DEC_ABS_X(0xDE, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),

	ASL_ACC(0x0A, MicroCycleProgram.internalShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
	ASL_ZPG(0x06, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ASL_ZPG_X(0x16, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ASL_ABS(0x0E, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ASL_ABS_X(0x1E, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),

	LSR_ACC(0x4A, MicroCycleProgram.internalShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
	LSR_ZPG(0x46, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	LSR_ZPG_X(0x56, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	LSR_ABS(0x4E, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	LSR_ABS_X(0x5E, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),

	ROL_ACC(0x2A, MicroCycleProgram.internalShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
	ROL_ZPG(0x26, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ROL_ZPG_X(0x36, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ROL_ABS(0x2E, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ROL_ABS_X(0x3E, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),

	ROR_ACC(0x6A, MicroCycleProgram.internalShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
	ROR_ZPG(0x66, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ROR_ZPG_X(0x76, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ROR_ABS(0x6E, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
	ROR_ABS_X(0x7E, MicroCycleProgram.rmwShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),

	ORA_IMM(0x09, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	ORA_ZPG(0x05, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	ORA_ZPG_X(0x15, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	ORA_ABS(0x0D, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	ORA_ABS_X(0x1D, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	ORA_ABS_Y(0x19, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	ORA_IND_X(0x01, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),
	ORA_IND_Y(0x11, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	ORA_IND(0x12, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),

	AND_IMM(0x29, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	AND_ZPG(0x25, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	AND_ZPG_X(0x35, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	AND_ABS(0x2D, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	AND_ABS_X(0x3D, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	AND_ABS_Y(0x39, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	AND_IND_X(0x21, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),
	AND_IND_Y(0x31, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	AND_IND(0x32, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),

	EOR_IMM(0x49, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	EOR_ZPG(0x45, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	EOR_ZPG_X(0x55, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	EOR_ABS(0x4D, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	EOR_ABS_X(0x5D, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	EOR_ABS_Y(0x59, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	EOR_IND_X(0x41, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),
	EOR_IND_Y(0x51, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	EOR_IND(0x52, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),

	ADC_IMM(0x69, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	ADC_ZPG(0x65, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	ADC_ZPG_X(0x75, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	ADC_ABS(0x6D, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	ADC_ABS_X(0x7D, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	ADC_ABS_Y(0x79, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	ADC_IND_X(0x61, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),
	ADC_IND_Y(0x71, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	ADC_IND(0x72, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),

	SBC_IMM(0xE9, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	SBC_ZPG(0xE5, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	SBC_ZPG_X(0xF5, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	SBC_ABS(0xED, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	SBC_ABS_X(0xFD, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	SBC_ABS_Y(0xF9, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	SBC_IND_X(0xE1, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),
	SBC_IND_Y(0xF1, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	SBC_IND(0xF2, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),

	CMP_IMM(0xC9, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	CMP_ZPG(0xC5, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	CMP_ZPG_X(0xD5, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	CMP_ABS(0xCD, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	CMP_ABS_X(0xDD, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	CMP_ABS_Y(0xD9, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	CMP_IND_X(0xC1, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),
	CMP_IND_Y(0xD1, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	CMP_IND(0xD2, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_ZP_PTR_LO, MicroOp.M_READ_ZP_PTR_HI, MicroOp.M_READ_EA))),

	BIT_IMM(0x89, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	BIT_ZPG(0x24, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	BIT_ZPG_X(0x34, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	BIT_ABS(0x2C, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	BIT_ABS_X(0x3C, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),

	LDX_IMM(0xA2, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	LDX_ZPG(0xA6, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	LDX_ZPG_Y(0xB6, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	LDX_ABS(0xAE, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	LDX_ABS_Y(0xBE, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),

	LDY_IMM(0xA0, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	LDY_ZPG(0xA4, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	LDY_ZPG_X(0xB4, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),
	LDY_ABS(0xAC, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),
	LDY_ABS_X(0xBC, MicroCycleProgram.readSplit(
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA),
			cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_READ_EA))),

	STX_ZPG(0x86, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_WRITE_EA))),
	STX_ZPG_Y(0x96, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_WRITE_EA))),
	STX_ABS(0x8E, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_WRITE_EA))),

	STY_ZPG(0x84, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_WRITE_EA))),
	STY_ZPG_X(0x94, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_WRITE_EA))),
	STY_ABS(0x8C, MicroCycleProgram.writeShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_WRITE_EA))),

	CPX_IMM(0xE0, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
	CPX_ZPG(0xE4, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
	CPX_ABS(0xEC, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),

		CPY_IMM(0xC0, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA))),
		CPY_ZPG(0xC4, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA))),
		CPY_ABS(0xCC, MicroCycleProgram.readShared(cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA))),

		JSR_ABS(0x20, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE,
				MicroOp.M_FETCH_OPERAND_LO,
				MicroOp.M_INTERNAL,
				MicroOp.M_INTERNAL,
				MicroOp.M_INTERNAL,
				MicroOp.M_FETCH_OPERAND_HI))),

		BRK_IMP(0x00, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL,
				MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		TSB_ZPG(0x04, MicroCycleProgram.rmwShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
		PHP_IMP(0x08, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		TSB_ABS(0x0C, MicroCycleProgram.rmwShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
		BPL_REL(0x10, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		TRB_ZPG(0x14, MicroCycleProgram.rmwShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
		CLC_IMP(0x18, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		INA_ACC(0x1A, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		TRB_ABS(0x1C, MicroCycleProgram.rmwShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_EA, MicroOp.M_WRITE_EA_DUMMY, MicroOp.M_WRITE_EA))),
		PLP_IMP(0x28, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		BMI_REL(0x30, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		SEC_IMP(0x38, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		DEA_ACC(0x3A, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		RTI_IMP(0x40, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		PHA_IMP(0x48, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		JMP_ABS(0x4C, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI))),
		BVC_REL(0x50, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		CLI_IMP(0x58, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		PHY_IMP(0x5A, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		RTS_IMP(0x60, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		STZ_ZPG(0x64, MicroCycleProgram.writeShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_WRITE_EA))),
		PLA_IMP(0x68, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		JMP_ABS_IND(0x6C, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI,
				MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		BVS_REL(0x70, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		STZ_ZPG_X(0x74, MicroCycleProgram.writeShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_READ_DUMMY, MicroOp.M_WRITE_EA))),
		SEI_IMP(0x78, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		PLY_IMP(0x7A, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		JMP_ABS_IND_X(0x7C, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI,
				MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		BRA_REL(0x80, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		DEY_IMP(0x88, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		TXA_IMP(0x8A, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		BCC_REL(0x90, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		TYA_IMP(0x98, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		TXS_IMP(0x9A, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		STZ_ABS(0x9C, MicroCycleProgram.writeShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_WRITE_EA))),
		STZ_ABS_X(0x9E, MicroCycleProgram.writeShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_FETCH_OPERAND_LO, MicroOp.M_FETCH_OPERAND_HI, MicroOp.M_READ_DUMMY, MicroOp.M_WRITE_EA))),
		TAY_IMP(0xA8, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		TAX_IMP(0xAA, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		BCS_REL(0xB0, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		CLV_IMP(0xB8, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		TSX_IMP(0xBA, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		INY_IMP(0xC8, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		DEX_IMP(0xCA, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		BNE_REL(0xD0, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		CLD_IMP(0xD8, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		PHX_IMP(0xDA, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL))),
		INX_IMP(0xE8, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		BEQ_REL(0xF0, MicroCycleProgram.internalSplit(
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA),
				cycles(MicroOp.M_FETCH_OPCODE, MicroOp.M_READ_IMM_DATA, MicroOp.M_INTERNAL))),
		SED_IMP(0xF8, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL))),
		PLX_IMP(0xFA, MicroCycleProgram.internalShared(cycles(
				MicroOp.M_FETCH_OPCODE, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL, MicroOp.M_INTERNAL)));

	private final int opcodeByte;
	private final MicroCycleProgram microcode;
	private static final EnumMap<Cpu65c02Opcode, Integer> OPCODE_BYTES = buildOpcodeByteMap();
	private static final EnumMap<Cpu65c02Opcode, MicroCycleProgram> MICROCODE_PROGRAMS = buildMicrocodeProgramMap();
	private static final Cpu65c02Opcode[] BYTE_TO_ENUM = buildByteToEnumMap();
	private static final EnumSet<Cpu65c02Opcode> LDA_FAMILY = EnumSet.of(
			LDA_IMM, LDA_ZPG, LDA_ZPG_X, LDA_ABS, LDA_ABS_X, LDA_ABS_Y, LDA_IND_X, LDA_IND_Y, LDA_IND);
	private static final EnumSet<Cpu65c02Opcode> STA_FAMILY = EnumSet.of(
			STA_ZPG, STA_ZPG_X, STA_ABS, STA_ABS_X, STA_ABS_Y, STA_IND_X, STA_IND_Y, STA_IND);
	private static final EnumSet<Cpu65c02Opcode> INC_FAMILY = EnumSet.of(
			INC_ZPG, INC_ZPG_X, INC_ABS, INC_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> DEC_FAMILY = EnumSet.of(
			DEC_ZPG, DEC_ZPG_X, DEC_ABS, DEC_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> ASL_FAMILY = EnumSet.of(
			ASL_ACC, ASL_ZPG, ASL_ZPG_X, ASL_ABS, ASL_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> LSR_FAMILY = EnumSet.of(
			LSR_ACC, LSR_ZPG, LSR_ZPG_X, LSR_ABS, LSR_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> ROL_FAMILY = EnumSet.of(
			ROL_ACC, ROL_ZPG, ROL_ZPG_X, ROL_ABS, ROL_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> ROR_FAMILY = EnumSet.of(
			ROR_ACC, ROR_ZPG, ROR_ZPG_X, ROR_ABS, ROR_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> ORA_FAMILY = EnumSet.of(
			ORA_IMM, ORA_ZPG, ORA_ZPG_X, ORA_ABS, ORA_ABS_X, ORA_ABS_Y, ORA_IND_X, ORA_IND_Y, ORA_IND);
	private static final EnumSet<Cpu65c02Opcode> AND_FAMILY = EnumSet.of(
			AND_IMM, AND_ZPG, AND_ZPG_X, AND_ABS, AND_ABS_X, AND_ABS_Y, AND_IND_X, AND_IND_Y, AND_IND);
	private static final EnumSet<Cpu65c02Opcode> EOR_FAMILY = EnumSet.of(
			EOR_IMM, EOR_ZPG, EOR_ZPG_X, EOR_ABS, EOR_ABS_X, EOR_ABS_Y, EOR_IND_X, EOR_IND_Y, EOR_IND);
	private static final EnumSet<Cpu65c02Opcode> ADC_FAMILY = EnumSet.of(
			ADC_IMM, ADC_ZPG, ADC_ZPG_X, ADC_ABS, ADC_ABS_X, ADC_ABS_Y, ADC_IND_X, ADC_IND_Y, ADC_IND);
	private static final EnumSet<Cpu65c02Opcode> SBC_FAMILY = EnumSet.of(
			SBC_IMM, SBC_ZPG, SBC_ZPG_X, SBC_ABS, SBC_ABS_X, SBC_ABS_Y, SBC_IND_X, SBC_IND_Y, SBC_IND);
	private static final EnumSet<Cpu65c02Opcode> CMP_FAMILY = EnumSet.of(
			CMP_IMM, CMP_ZPG, CMP_ZPG_X, CMP_ABS, CMP_ABS_X, CMP_ABS_Y, CMP_IND_X, CMP_IND_Y, CMP_IND);
	private static final EnumSet<Cpu65c02Opcode> BIT_FAMILY = EnumSet.of(
			BIT_IMM, BIT_ZPG, BIT_ZPG_X, BIT_ABS, BIT_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> LDX_FAMILY = EnumSet.of(
			LDX_IMM, LDX_ZPG, LDX_ZPG_Y, LDX_ABS, LDX_ABS_Y);
	private static final EnumSet<Cpu65c02Opcode> LDY_FAMILY = EnumSet.of(
			LDY_IMM, LDY_ZPG, LDY_ZPG_X, LDY_ABS, LDY_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> STX_FAMILY = EnumSet.of(
			STX_ZPG, STX_ZPG_Y, STX_ABS);
	private static final EnumSet<Cpu65c02Opcode> STY_FAMILY = EnumSet.of(
			STY_ZPG, STY_ZPG_X, STY_ABS);
	private static final EnumSet<Cpu65c02Opcode> CPX_FAMILY = EnumSet.of(
			CPX_IMM, CPX_ZPG, CPX_ABS);
	private static final EnumSet<Cpu65c02Opcode> CPY_FAMILY = EnumSet.of(
			CPY_IMM, CPY_ZPG, CPY_ABS);
	private static final EnumSet<Cpu65c02Opcode> JUMP_FAMILY = EnumSet.of(
			JMP_ABS, JMP_ABS_IND, JMP_ABS_IND_X, JSR_ABS);
	private static final EnumSet<Cpu65c02Opcode> BIT_TEST_SET_FAMILY = EnumSet.of(
			BIT_IMM, BIT_ZPG, BIT_ZPG_X, BIT_ABS, BIT_ABS_X, TSB_ZPG, TSB_ABS, TRB_ZPG, TRB_ABS);
	private static final EnumSet<Cpu65c02Opcode> TRANSFER_FAMILY = EnumSet.of(
			TAX_IMP, TAY_IMP, TXA_IMP, TYA_IMP, TSX_IMP, TXS_IMP);
	private static final EnumSet<Cpu65c02Opcode> INDEX_INCDEC_FAMILY = EnumSet.of(
			INX_IMP, INY_IMP, DEX_IMP, DEY_IMP, INA_ACC, DEA_ACC);
	private static final EnumSet<Cpu65c02Opcode> STZ_FAMILY = EnumSet.of(
			STZ_ZPG, STZ_ZPG_X, STZ_ABS, STZ_ABS_X);
	private static final EnumSet<Cpu65c02Opcode> INTERRUPT_CONTROL_FLOW_FAMILY = EnumSet.of(
			BRK_IMP, RTI_IMP, RTS_IMP, JSR_ABS, JMP_ABS, JMP_ABS_IND, JMP_ABS_IND_X,
			BPL_REL, BMI_REL, BVC_REL, BVS_REL, BRA_REL, BCC_REL, BCS_REL, BNE_REL, BEQ_REL);
	private static final EnumSet<Cpu65c02Opcode> STACK_FAMILY = EnumSet.of(
			BRK_IMP, PHP_IMP, PLP_IMP, RTI_IMP, PHA_IMP, PLA_IMP, RTS_IMP, PHY_IMP, PLY_IMP, PHX_IMP, PLX_IMP);
	private static final EnumSet<Cpu65c02Opcode> FLAGS_FAMILY = EnumSet.of(
			CLC_IMP, SEC_IMP, CLI_IMP, SEI_IMP, CLV_IMP, CLD_IMP, SED_IMP);
	private static final EnumSet<Cpu65c02Opcode> JSR_FAMILY = EnumSet.of(
				JSR_ABS);
	private static final EnumSet<Cpu65c02Opcode> BRANCH_FAMILY = EnumSet.of(
			BPL_REL, BMI_REL, BVC_REL, BVS_REL, BRA_REL, BCC_REL, BCS_REL, BNE_REL, BEQ_REL);
	private static final EnumSet<Cpu65c02Opcode> CONTROL_MISC_FAMILY = EnumSet.of(
				BRK_IMP, TSB_ZPG, PHP_IMP, TSB_ABS, BPL_REL, TRB_ZPG, CLC_IMP, INA_ACC, TRB_ABS, PLP_IMP,
				BMI_REL, SEC_IMP, DEA_ACC, RTI_IMP, PHA_IMP, JMP_ABS, BVC_REL, CLI_IMP, PHY_IMP, RTS_IMP,
				STZ_ZPG, PLA_IMP, JMP_ABS_IND, BVS_REL, STZ_ZPG_X, SEI_IMP, PLY_IMP, JMP_ABS_IND_X, BRA_REL,
				DEY_IMP, TXA_IMP, BCC_REL, TYA_IMP, TXS_IMP, STZ_ABS, STZ_ABS_X, TAY_IMP, TAX_IMP, BCS_REL,
				CLV_IMP, TSX_IMP, INY_IMP, DEX_IMP, BNE_REL, CLD_IMP, PHX_IMP, INX_IMP, BEQ_REL, SED_IMP, PLX_IMP);

	Cpu65c02Opcode(int opcodeByte, MicroCycleProgram microcode) {
		this.opcodeByte = opcodeByte & 0xff;
		this.microcode = microcode;
	}

	public int opcodeByte() {
		return OPCODE_BYTES.get(this).intValue();
	}

	public MicroCycleProgram microcode() {
		return MICROCODE_PROGRAMS.get(this);
	}

	public static int[] ldaOpcodeBytes() {
		return buildLdaOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> ldaFamily() {
		return EnumSet.copyOf(LDA_FAMILY);
	}

	public static Cpu65c02Opcode fromOpcodeByte(int opcodeByte) {
		return BYTE_TO_ENUM[opcodeByte & 0xff];
	}

	public static EnumSet<Cpu65c02Opcode> staFamily() {
		return EnumSet.copyOf(STA_FAMILY);
	}

	public static int[] staOpcodeBytes() {
		return buildStaOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> incFamily() {
		return EnumSet.copyOf(INC_FAMILY);
	}

	public static int[] incOpcodeBytes() {
		return buildIncOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> decFamily() {
		return EnumSet.copyOf(DEC_FAMILY);
	}

	public static int[] decOpcodeBytes() {
		return buildDecOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> aslFamily() {
		return EnumSet.copyOf(ASL_FAMILY);
	}

	public static int[] aslOpcodeBytes() {
		return buildAslOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> lsrFamily() {
		return EnumSet.copyOf(LSR_FAMILY);
	}

	public static int[] lsrOpcodeBytes() {
		return buildLsrOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> rolFamily() {
		return EnumSet.copyOf(ROL_FAMILY);
	}

	public static int[] rolOpcodeBytes() {
		return buildRolOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> rorFamily() {
		return EnumSet.copyOf(ROR_FAMILY);
	}

	public static int[] rorOpcodeBytes() {
		return buildRorOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> oraFamily() {
		return EnumSet.copyOf(ORA_FAMILY);
	}

	public static int[] oraOpcodeBytes() {
		return buildOraOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> andFamily() {
		return EnumSet.copyOf(AND_FAMILY);
	}

	public static int[] andOpcodeBytes() {
		return buildAndOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> eorFamily() {
		return EnumSet.copyOf(EOR_FAMILY);
	}

	public static int[] eorOpcodeBytes() {
		return buildEorOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> adcFamily() {
		return EnumSet.copyOf(ADC_FAMILY);
	}

	public static int[] adcOpcodeBytes() {
		return buildAdcOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> sbcFamily() {
		return EnumSet.copyOf(SBC_FAMILY);
	}

	public static int[] sbcOpcodeBytes() {
		return buildSbcOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> cmpFamily() {
		return EnumSet.copyOf(CMP_FAMILY);
	}

	public static int[] cmpOpcodeBytes() {
		return buildCmpOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> bitFamily() {
		return EnumSet.copyOf(BIT_FAMILY);
	}

	public static int[] bitOpcodeBytes() {
		return buildBitOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> ldxFamily() {
		return EnumSet.copyOf(LDX_FAMILY);
	}

	public static int[] ldxOpcodeBytes() {
		return buildLdxOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> ldyFamily() {
		return EnumSet.copyOf(LDY_FAMILY);
	}

	public static int[] ldyOpcodeBytes() {
		return buildLdyOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> stxFamily() {
		return EnumSet.copyOf(STX_FAMILY);
	}

	public static int[] stxOpcodeBytes() {
		return buildStxOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> styFamily() {
		return EnumSet.copyOf(STY_FAMILY);
	}

	public static int[] styOpcodeBytes() {
		return buildStyOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> cpxFamily() {
		return EnumSet.copyOf(CPX_FAMILY);
	}

	public static int[] cpxOpcodeBytes() {
		return buildCpxOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> cpyFamily() {
		return EnumSet.copyOf(CPY_FAMILY);
	}

	public static int[] cpyOpcodeBytes() {
		return buildCpyOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> jumpFamily() {
		return EnumSet.copyOf(JUMP_FAMILY);
	}

	public static int[] jumpOpcodeBytes() {
		return buildJumpOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> bitTestSetFamily() {
		return EnumSet.copyOf(BIT_TEST_SET_FAMILY);
	}

	public static int[] bitTestSetOpcodeBytes() {
		return buildBitTestSetOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> transferFamily() {
		return EnumSet.copyOf(TRANSFER_FAMILY);
	}

	public static int[] transferOpcodeBytes() {
		return buildTransferOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> indexIncDecFamily() {
		return EnumSet.copyOf(INDEX_INCDEC_FAMILY);
	}

	public static int[] indexIncDecOpcodeBytes() {
		return buildIndexIncDecOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> stzFamily() {
		return EnumSet.copyOf(STZ_FAMILY);
	}

	public static int[] stzOpcodeBytes() {
		return buildStzOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> interruptControlFlowFamily() {
		return EnumSet.copyOf(INTERRUPT_CONTROL_FLOW_FAMILY);
	}

	public static int[] interruptControlFlowOpcodeBytes() {
		return buildInterruptControlFlowOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> stackFamily() {
		return EnumSet.copyOf(STACK_FAMILY);
	}

	public static int[] stackOpcodeBytes() {
		return buildStackOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> flagsFamily() {
		return EnumSet.copyOf(FLAGS_FAMILY);
	}

	public static int[] flagsOpcodeBytes() {
		return buildFlagsOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> jsrFamily() {
		return EnumSet.copyOf(JSR_FAMILY);
	}

	public static int[] jsrOpcodeBytes() {
		return buildJsrOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> branchFamily() {
		return EnumSet.copyOf(BRANCH_FAMILY);
	}

	public static int[] branchOpcodeBytes() {
		return buildBranchOpcodeBytes();
	}

	public static EnumSet<Cpu65c02Opcode> controlMiscFamily() {
		return EnumSet.copyOf(CONTROL_MISC_FAMILY);
	}

	public static int[] controlMiscOpcodeBytes() {
		return buildControlMiscOpcodeBytes();
	}

	private static int[] buildLdaOpcodeBytes() {
		Cpu65c02Opcode[] ops = LDA_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildStaOpcodeBytes() {
		Cpu65c02Opcode[] ops = STA_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildIncOpcodeBytes() {
		Cpu65c02Opcode[] ops = INC_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildDecOpcodeBytes() {
		Cpu65c02Opcode[] ops = DEC_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildAslOpcodeBytes() {
		Cpu65c02Opcode[] ops = ASL_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildLsrOpcodeBytes() {
		Cpu65c02Opcode[] ops = LSR_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildRolOpcodeBytes() {
		Cpu65c02Opcode[] ops = ROL_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildRorOpcodeBytes() {
		Cpu65c02Opcode[] ops = ROR_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildOraOpcodeBytes() {
		Cpu65c02Opcode[] ops = ORA_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildAndOpcodeBytes() {
		Cpu65c02Opcode[] ops = AND_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildEorOpcodeBytes() {
		Cpu65c02Opcode[] ops = EOR_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildAdcOpcodeBytes() {
		Cpu65c02Opcode[] ops = ADC_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildSbcOpcodeBytes() {
		Cpu65c02Opcode[] ops = SBC_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildCmpOpcodeBytes() {
		Cpu65c02Opcode[] ops = CMP_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildBitOpcodeBytes() {
		Cpu65c02Opcode[] ops = BIT_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildLdxOpcodeBytes() {
		Cpu65c02Opcode[] ops = LDX_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildLdyOpcodeBytes() {
		Cpu65c02Opcode[] ops = LDY_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildStxOpcodeBytes() {
		Cpu65c02Opcode[] ops = STX_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildStyOpcodeBytes() {
		Cpu65c02Opcode[] ops = STY_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildCpxOpcodeBytes() {
		Cpu65c02Opcode[] ops = CPX_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildCpyOpcodeBytes() {
		Cpu65c02Opcode[] ops = CPY_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildJumpOpcodeBytes() {
		Cpu65c02Opcode[] ops = JUMP_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildBitTestSetOpcodeBytes() {
		Cpu65c02Opcode[] ops = BIT_TEST_SET_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildTransferOpcodeBytes() {
		Cpu65c02Opcode[] ops = TRANSFER_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildIndexIncDecOpcodeBytes() {
		Cpu65c02Opcode[] ops = INDEX_INCDEC_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildStzOpcodeBytes() {
		Cpu65c02Opcode[] ops = STZ_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildInterruptControlFlowOpcodeBytes() {
		Cpu65c02Opcode[] ops = INTERRUPT_CONTROL_FLOW_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildStackOpcodeBytes() {
		Cpu65c02Opcode[] ops = STACK_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildFlagsOpcodeBytes() {
		Cpu65c02Opcode[] ops = FLAGS_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildJsrOpcodeBytes() {
		Cpu65c02Opcode[] ops = JSR_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildBranchOpcodeBytes() {
		Cpu65c02Opcode[] ops = BRANCH_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static int[] buildControlMiscOpcodeBytes() {
		Cpu65c02Opcode[] ops = CONTROL_MISC_FAMILY.toArray(new Cpu65c02Opcode[0]);
		int[] bytes = new int[ops.length];
		for( int i = 0; i<ops.length; i++ )
			bytes[i] = ops[i].opcodeByte();
		return bytes;
	}

	private static EnumMap<Cpu65c02Opcode, Integer> buildOpcodeByteMap() {
		EnumMap<Cpu65c02Opcode, Integer> map = new EnumMap<Cpu65c02Opcode, Integer>(Cpu65c02Opcode.class);
		for( Cpu65c02Opcode opcode : Cpu65c02Opcode.values() )
			map.put(opcode, Integer.valueOf(opcode.opcodeByte));
		return map;
	}

	private static EnumMap<Cpu65c02Opcode, MicroCycleProgram> buildMicrocodeProgramMap() {
		EnumMap<Cpu65c02Opcode, MicroCycleProgram> map = new EnumMap<Cpu65c02Opcode, MicroCycleProgram>(Cpu65c02Opcode.class);
		for( Cpu65c02Opcode opcode : Cpu65c02Opcode.values() )
			map.put(opcode, opcode.microcode);
		return map;
	}

	private static Cpu65c02Opcode[] buildByteToEnumMap() {
		Cpu65c02Opcode[] map = new Cpu65c02Opcode[256];
		for( Cpu65c02Opcode opcode : Cpu65c02Opcode.values() ) {
			int byteValue = opcode.opcodeByte;
			if( map[byteValue]!=null )
				throw new IllegalStateException("Duplicate opcode byte: " + byteValue);
			map[byteValue] = opcode;
		}
		return map;
	}

	private static MicroOp[] cycles(MicroOp... ops) {
		return ops;
	}

	public static final class MicroCycleProgram {
		private final AccessType accessType;
		private final MicroOp[] noCrossScript;
		private final MicroOp[] crossScript;

		private MicroCycleProgram(AccessType accessType, MicroOp[] noCrossScript, MicroOp[] crossScript) {
			this.accessType = accessType;
			this.noCrossScript = Arrays.copyOf(noCrossScript, noCrossScript.length);
			this.crossScript = Arrays.copyOf(crossScript, crossScript.length);
		}

		public static MicroCycleProgram readShared(MicroOp... script) {
			return new MicroCycleProgram(AccessType.AT_READ, script, script);
		}

		public static MicroCycleProgram readSplit(MicroOp[] noCrossScript, MicroOp[] crossScript) {
			return new MicroCycleProgram(AccessType.AT_READ, noCrossScript, crossScript);
		}

		public static MicroCycleProgram writeShared(MicroOp... script) {
			return new MicroCycleProgram(AccessType.AT_WRITE, script, script);
		}

		public static MicroCycleProgram rmwShared(MicroOp... script) {
			return new MicroCycleProgram(AccessType.AT_RMW, script, script);
		}

		public static MicroCycleProgram internalShared(MicroOp... script) {
			return new MicroCycleProgram(AccessType.AT_NONE, script, script);
		}

		public static MicroCycleProgram internalSplit(MicroOp[] noCrossScript, MicroOp[] crossScript) {
			return new MicroCycleProgram(AccessType.AT_NONE, noCrossScript, crossScript);
		}

		public AccessType accessType() {
			return accessType;
		}

		public MicroOp[] noCrossScript() {
			return Arrays.copyOf(noCrossScript, noCrossScript.length);
		}

		public MicroOp[] crossScript() {
			return Arrays.copyOf(crossScript, crossScript.length);
		}
	}
}
