package test.cpu;

import core.cpu.cpu8.Cpu65c02;
import core.cpu.cpu8.Cpu65c02Cmd;
import core.cpu.cpu8.Cpu65c02CycleEstimator;
import core.cpu.cpu8.Opcode;
import core.emulator.HardwareManager;
import core.emulator.machine.Emulator;
import core.cpu.cpu8.Register;
import core.exception.HardwareException;
import core.memory.memory8.Memory8;
import core.memory.memory8.MemoryBusIIe;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.PriorityQueue;

import static org.junit.Assert.assertEquals;

public class Cpu65c02CycleTimingTest {

    private static final int MEM_SIZE = 0x20000;
    private static final int ROM_SIZE = 0x4000;
    private static final int PROG_PC = 0x0200;
    private static final String[] CPU_PROFILES = new String[] { "wdc", "cmd" };
    private static final int[][] CMD_NOP_CYCLE_EXPECTATIONS = new int[][] {
            { 0x02, 2 }, { 0x03, 1 }, { 0x07, 1 }, { 0x0B, 1 }, { 0x0F, 1 }, { 0x13, 1 }, { 0x17, 1 }, { 0x1B, 1 },
            { 0x1F, 1 }, { 0x22, 2 }, { 0x23, 1 }, { 0x27, 1 }, { 0x2B, 1 }, { 0x2F, 1 }, { 0x33, 1 }, { 0x37, 1 },
            { 0x3B, 1 }, { 0x3F, 1 }, { 0x42, 2 }, { 0x43, 1 }, { 0x44, 3 }, { 0x47, 1 }, { 0x4B, 1 }, { 0x4F, 1 },
            { 0x53, 1 }, { 0x54, 4 }, { 0x57, 1 }, { 0x5B, 1 }, { 0x5C, 8 }, { 0x5F, 1 }, { 0x62, 2 }, { 0x63, 1 },
            { 0x67, 1 }, { 0x6B, 1 }, { 0x6F, 1 }, { 0x73, 1 }, { 0x77, 1 }, { 0x7B, 1 }, { 0x7F, 1 }, { 0x82, 2 },
            { 0x83, 1 }, { 0x87, 1 }, { 0x8B, 1 }, { 0x8F, 1 }, { 0x93, 1 }, { 0x97, 1 }, { 0x9B, 1 }, { 0x9F, 1 },
            { 0xA3, 1 }, { 0xA7, 1 }, { 0xAB, 1 }, { 0xAF, 1 }, { 0xB3, 1 }, { 0xB7, 1 }, { 0xBB, 1 }, { 0xBF, 1 },
            { 0xC2, 2 }, { 0xC3, 1 }, { 0xC7, 1 }, { 0xCB, 1 }, { 0xCF, 1 }, { 0xD3, 1 }, { 0xD4, 4 }, { 0xD7, 1 },
            { 0xDB, 1 }, { 0xDC, 4 }, { 0xDF, 1 }, { 0xE2, 2 }, { 0xE3, 1 }, { 0xE7, 1 }, { 0xEA, 2 }, { 0xEB, 1 },
            { 0xEF, 1 }, { 0xF3, 1 }, { 0xF4, 4 }, { 0xF7, 1 }, { 0xFB, 1 }, { 0xFC, 4 }, { 0xFF, 1 },
    };
    private static final int[][] CMD_NOP_WIDTH_EXPECTATIONS = new int[][] {
            { 0x07, 2 }, { 0x0F, 3 }, { 0x17, 2 }, { 0x1F, 3 }, { 0x27, 2 }, { 0x2F, 3 }, { 0x37, 2 }, { 0x3F, 3 },
            { 0x47, 2 }, { 0x4F, 3 }, { 0x57, 2 }, { 0x5F, 3 }, { 0x67, 2 }, { 0x6F, 3 }, { 0x77, 2 }, { 0x7F, 3 },
            { 0x87, 2 }, { 0x8F, 3 }, { 0x97, 2 }, { 0x9F, 3 }, { 0xA7, 2 }, { 0xAF, 3 }, { 0xB7, 2 }, { 0xBF, 3 },
            { 0xC7, 2 }, { 0xCF, 3 }, { 0xD7, 2 }, { 0xDF, 3 }, { 0xE7, 2 }, { 0xEF, 3 }, { 0xF7, 2 }, { 0xFF, 3 },
    };

    private static final class CpuEnv {
        final MemoryBusIIe bus;
        final Cpu65c02 cpu;
        final Emulator emulator;
        final Register reg;

        CpuEnv(MemoryBusIIe bus, Cpu65c02 cpu, Emulator emulator) {
            this.bus = bus;
            this.cpu = cpu;
            this.emulator = emulator;
            this.reg = cpu.getRegister();
        }
    }

    private CpuEnv createEnv(String profile) throws HardwareException {
        Memory8 mem = new Memory8(MEM_SIZE);
        byte[] rom = new byte[ROM_SIZE];
        MemoryBusIIe bus = new MemoryBusIIe(mem, rom);
        Cpu65c02 cpu = "cmd".equals(profile) ? new Cpu65c02Cmd(bus, 0) : new Cpu65c02(bus, 0);
        PriorityQueue<HardwareManager> queue = new PriorityQueue<HardwareManager>();
        queue.add(cpu);
        Emulator emulator = new Emulator(queue, 0);
        cpu.coldReset();
        rom[0x3ffc] = (byte) (PROG_PC & 0xFF);
        rom[0x3ffd] = (byte) ((PROG_PC >> 8) & 0xFF);
        return new CpuEnv(bus, cpu, emulator);
    }

    private void loadProgram(CpuEnv env, int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            env.bus.setByte(PROG_PC + i, bytes[i] & 0xFF);
        }
    }

    private void runInstruction(CpuEnv env) throws Exception {
        while (true) {
            boolean instructionEndsThisCycle = env.cpu.hasPendingInstructionEndEvent();
            env.emulator.startWithStepPhases(1, env.cpu, (step, manager, preCycle) -> true);
            if (instructionEndsThisCycle) {
                return;
            }
        }
    }

    private int estimateCycles(CpuEnv env, int opcodeByte) {
        return Cpu65c02CycleEstimator.predictInstructionCycles(env.bus, env.reg, Cpu65c02.OPCODE[opcodeByte & 0xFF], PROG_PC);
    }

    private Opcode[] opcodeTableFor(Cpu65c02 cpu) throws Exception {
        Method m = Cpu65c02.class.getDeclaredMethod("getOpcodeTable");
        m.setAccessible(true);
        return (Opcode[]) m.invoke(cpu);
    }

    @Test
    public void ldaAbsXAddsCycleOnPageCrossOnly() throws Exception {
        for( String profile : CPU_PROFILES ) {
            CpuEnv env = createEnv(profile);
            loadProgram(env, 0xBD, 0xFE, 0x20); // LDA $20FE,X
            runInstruction(env); // execute reset after program bytes are loaded
            env.reg.setX(0x01);
            env.bus.setByte(0x20FF, 0x11);
            runInstruction(env);
            assertEquals(estimateCycles(env, 0xBD), env.cpu.getLastInstructionCycleCount());

            env = createEnv(profile);
            loadProgram(env, 0xBD, 0xFF, 0x20); // LDA $20FF,X
            runInstruction(env);
            env.reg.setX(0x01);
            env.bus.setByte(0x2100, 0x22);
            runInstruction(env);
            assertEquals(estimateCycles(env, 0xBD), env.cpu.getLastInstructionCycleCount());
        }
    }

    @Test
    public void ldaAbsYAddsCycleOnPageCrossOnly() throws Exception {
        for( String profile : CPU_PROFILES ) {
            CpuEnv env = createEnv(profile);
            loadProgram(env, 0xB9, 0xFE, 0x20); // LDA $20FE,Y
            runInstruction(env);
            env.reg.setY(0x01);
            env.bus.setByte(0x20FF, 0x33);
            runInstruction(env);
            assertEquals(estimateCycles(env, 0xB9), env.cpu.getLastInstructionCycleCount());

            env = createEnv(profile);
            loadProgram(env, 0xB9, 0xFF, 0x20); // LDA $20FF,Y
            runInstruction(env);
            env.reg.setY(0x01);
            env.bus.setByte(0x2100, 0x44);
            runInstruction(env);
            assertEquals(estimateCycles(env, 0xB9), env.cpu.getLastInstructionCycleCount());
        }
    }

    @Test
    public void ldaIndYAddsCycleOnPageCrossOnly() throws Exception {
        for( String profile : CPU_PROFILES ) {
            CpuEnv env = createEnv(profile);
            loadProgram(env, 0xB1, 0x10); // LDA ($10),Y
            runInstruction(env);
            env.reg.setY(0x01);
            env.bus.setByte(0x0010, 0xFE);
            env.bus.setByte(0x0011, 0x20);
            env.bus.setByte(0x20FF, 0x55);
            runInstruction(env);
            assertEquals(estimateCycles(env, 0xB1), env.cpu.getLastInstructionCycleCount());

            env = createEnv(profile);
            loadProgram(env, 0xB1, 0x10); // LDA ($10),Y
            runInstruction(env);
            env.reg.setY(0x01);
            env.bus.setByte(0x0010, 0xFF);
            env.bus.setByte(0x0011, 0x20);
            env.bus.setByte(0x2100, 0x66);
            runInstruction(env);
            assertEquals(estimateCycles(env, 0xB1), env.cpu.getLastInstructionCycleCount());
        }
    }

    @Test
    public void jmpAbsIndXHasNoPageCrossPenalty() throws Exception {
        for( String profile : CPU_PROFILES ) {
            CpuEnv env = createEnv(profile);
            loadProgram(env, 0x7C, 0xFF, 0x20); // JMP ($20FF,X)
            runInstruction(env);
            env.reg.setX(0x01);
            env.bus.setByte(0x2100, 0x34);
            env.bus.setByte(0x2101, 0x12);
            runInstruction(env);
            assertEquals(6, env.cpu.getLastInstructionCycleCount());
            assertEquals(0x1234, env.cpu.getPendingPC());
        }
    }

    @Test
    public void staAbsXHasNoExtraPageCrossCycle() throws Exception {
        for( String profile : CPU_PROFILES ) {
            CpuEnv env = createEnv(profile);
            loadProgram(env, 0x9D, 0xFF, 0x20); // STA $20FF,X
            runInstruction(env);
            env.reg.setA(0xA5);
            env.reg.setX(0x01);
            runInstruction(env);
            assertEquals(5, env.cpu.getLastInstructionCycleCount());
            assertEquals(0xA5, env.bus.getByte(0x2100));
        }
    }

    @Test
    public void cmdProfileUsesHardwareValidatedNopTimingAndLength() throws Exception {
        CpuEnv env = createEnv("cmd");
        loadProgram(env, 0x03, 0x00, 0x00); // CMD undocumented NOP
        runInstruction(env); // reset
        runInstruction(env);
        assertEquals(1, env.cpu.getLastInstructionCycleCount());
        assertEquals(PROG_PC + 1, env.cpu.getPendingPC());

        env = createEnv("cmd");
        loadProgram(env, 0x5C, 0x00, 0x00); // CMD undocumented long NOP
        runInstruction(env); // reset
        runInstruction(env);
        assertEquals(8, env.cpu.getLastInstructionCycleCount());
        assertEquals(PROG_PC + 3, env.cpu.getPendingPC());
    }

    @Test
    public void cmdProfileAllMeasuredNopCyclesMatchHardwareTable() throws Exception {
        CpuEnv env = createEnv("cmd");
        Opcode[] table = opcodeTableFor(env.cpu);
        for( int[] expected : CMD_NOP_CYCLE_EXPECTATIONS ) {
            int opcode = expected[0] & 0xFF;
            int cycles = expected[1];
            Opcode actual = table[opcode];
            assertEquals("CMD opcode should be NOP for $" + String.format("%02X", opcode),
                    Cpu65c02.OpcodeMnemonic.NOP, actual.getMnemonic());
            assertEquals("CMD cycle mismatch for $" + String.format("%02X", opcode),
                    cycles, actual.getCycleTime());
        }
    }

    @Test
    public void cmdProfileMeasuredCompatibilityNopWidthsMatchMameTable() throws Exception {
        CpuEnv env = createEnv("cmd");
        Opcode[] table = opcodeTableFor(env.cpu);
        for( int[] expected : CMD_NOP_WIDTH_EXPECTATIONS ) {
            int opcode = expected[0] & 0xFF;
            int width = expected[1];
            Opcode actual = table[opcode];
            assertEquals("CMD opcode should be NOP for $" + String.format("%02X", opcode),
                    Cpu65c02.OpcodeMnemonic.NOP, actual.getMnemonic());
            assertEquals("CMD width mismatch for $" + String.format("%02X", opcode),
                    width, actual.getInstrSize());
        }
    }
}
