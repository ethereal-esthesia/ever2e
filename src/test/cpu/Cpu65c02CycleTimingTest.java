package test.cpu;

import core.cpu.cpu8.Cpu65c02;
import core.cpu.cpu8.Cpu65c02Cmd;
import core.cpu.cpu8.Cpu65c02CycleEstimator;
import core.emulator.HardwareManager;
import core.emulator.machine.Emulator;
import core.cpu.cpu8.Register;
import core.exception.HardwareException;
import core.memory.memory8.Memory8;
import core.memory.memory8.MemoryBusIIe;
import org.junit.Test;

import java.util.PriorityQueue;

import static org.junit.Assert.assertEquals;

public class Cpu65c02CycleTimingTest {

    private static final int MEM_SIZE = 0x20000;
    private static final int ROM_SIZE = 0x4000;
    private static final int PROG_PC = 0x0200;
    private static final String[] CPU_PROFILES = new String[] { "wdc", "cmd" };

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
}
