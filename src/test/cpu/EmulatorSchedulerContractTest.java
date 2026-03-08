package test.cpu;

import core.cpu.cpu8.Cpu65c02;
import core.cpu.cpu8.Cpu65c02Cmd;
import core.cpu.cpu8.Cpu65c02CycleEstimator;
import core.cpu.cpu8.Opcode;
import core.emulator.HardwareManager;
import core.emulator.machine.Emulator;
import core.exception.HardwareException;
import core.memory.memory8.Memory8;
import core.memory.memory8.MemoryBusIIe;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EmulatorSchedulerContractTest {

    private static final int MEM_SIZE = 0x20000;
    private static final int ROM_SIZE = 0x4000;
    private static final int PROG_PC = 0x0200;
    private static final String[] CPU_PROFILES = new String[] { "wdc", "cmd" };

    private static final class Env {
        final MemoryBusIIe bus;
        final byte[] rom;
        final Cpu65c02 cpu;
        final Emulator emulator;

        Env(MemoryBusIIe bus, byte[] rom, Cpu65c02 cpu, Emulator emulator) {
            this.bus = bus;
            this.rom = rom;
            this.cpu = cpu;
            this.emulator = emulator;
        }
    }

    private static final class OrderedProbeManager extends HardwareManager {
        private final String name;
        private final List<String> runLog;

        OrderedProbeManager(String name, List<String> runLog) {
            super(1);
            this.name = name;
            this.runLog = runLog;
        }

        @Override
        public void coldReset() {
            resetCycleCount();
        }

        @Override
        public void cycle() {
            runLog.add(name);
            incSleepCycles(1);
        }
    }

    private static final class InterruptAtTickManager extends HardwareManager {
        private final Cpu65c02 cpu;
        private final Opcode interrupt;
        private final int fireTick;
        private int tick;
        private boolean fired;

        InterruptAtTickManager(Cpu65c02 cpu, Opcode interrupt, int fireTick) {
            super(1);
            this.cpu = cpu;
            this.interrupt = interrupt;
            this.fireTick = fireTick;
        }

        @Override
        public void coldReset() {
            resetCycleCount();
            tick = 0;
            fired = false;
        }

        @Override
        public void cycle() {
            tick++;
            if (!fired && tick >= fireTick) {
                cpu.setInterruptPending(interrupt);
                fired = true;
            }
            incSleepCycles(1);
        }
    }

    private Env createEnv(String profile, HardwareManager... extraManagers) throws HardwareException {
        Memory8 mem = new Memory8(MEM_SIZE);
        byte[] rom = new byte[ROM_SIZE];
        MemoryBusIIe bus = new MemoryBusIIe(mem, rom);
        Cpu65c02 cpu = "cmd".equals(profile) ? new Cpu65c02Cmd(bus, 1) : new Cpu65c02(bus, 1);

        PriorityQueue<HardwareManager> queue = new PriorityQueue<HardwareManager>();
        queue.add(cpu);
        for (HardwareManager manager : extraManagers)
            queue.add(manager);

        Emulator emulator = new Emulator(queue, 0);
        return new Env(bus, rom, cpu, emulator);
    }

    private void setVector(byte[] rom, int vectorAddr, int targetAddr) {
        int idx = vectorAddr - 0xC000;
        rom[idx] = (byte) (targetAddr & 0xFF);
        rom[idx + 1] = (byte) ((targetAddr >> 8) & 0xFF);
    }

    private void loadProgram(Env env, int start, int... bytes) {
        for (int i = 0; i < bytes.length; i++)
            env.bus.setByte(start + i, bytes[i] & 0xFF);
    }

    private void prepareRepresentativeProgram(Env env, int opcodeByte) {
        switch (opcodeByte & 0xFF) {
            case 0xA9: // LDA #imm
            case 0x09: // ORA #imm
            case 0x29: // AND #imm
            case 0x49: // EOR #imm
            case 0x69: // ADC #imm
            case 0xE9: // SBC #imm
            case 0xC9: // CMP #imm
            case 0x89: // BIT #imm
            case 0xA2: // LDX #imm
            case 0xA0: // LDY #imm
            case 0xE0: // CPX #imm
            case 0xC0: // CPY #imm
                loadProgram(env, PROG_PC, opcodeByte, 0x42, 0xEA);
                break;
            case 0x85: // STA zpg
                loadProgram(env, PROG_PC, opcodeByte, 0x10, 0xEA);
                env.cpu.getRegister().setA(0x77);
                break;
            case 0x86: // STX zpg
                loadProgram(env, PROG_PC, opcodeByte, 0x10, 0xEA);
                env.cpu.getRegister().setX(0x66);
                break;
            case 0x84: // STY zpg
                loadProgram(env, PROG_PC, opcodeByte, 0x10, 0xEA);
                env.cpu.getRegister().setY(0x55);
                break;
            case 0xE6: // INC zpg
            case 0xC6: // DEC zpg
                loadProgram(env, PROG_PC, opcodeByte, 0x10, 0xEA);
                env.bus.setByte(0x0010, 0x10);
                break;
            case 0x0A: // ASL A
            case 0x4A: // LSR A
            case 0x2A: // ROL A
            case 0x6A: // ROR A
                loadProgram(env, PROG_PC, opcodeByte, 0xEA, 0xEA);
                env.cpu.getRegister().setA(0x81);
                break;
            default:
                throw new IllegalArgumentException("Unsupported representative opcode " + Integer.toHexString(opcodeByte));
        }
    }

    @Test
    public void equalTimestampManagersRunInStableIdOrder() throws Exception {
        List<String> runLog = new ArrayList<String>();
        OrderedProbeManager a = new OrderedProbeManager("A", runLog);
        OrderedProbeManager b = new OrderedProbeManager("B", runLog);
        OrderedProbeManager c = new OrderedProbeManager("C", runLog);

        PriorityQueue<HardwareManager> queue = new PriorityQueue<HardwareManager>();
        queue.add(c);
        queue.add(a);
        queue.add(b);

        Emulator emulator = new Emulator(queue, 0);
        emulator.startWithStepPhases(5, a, (step, manager, preCycle) -> true);

        assertTrue(runLog.size() >= 9);
        assertEquals("A", runLog.get(0));
        assertEquals("B", runLog.get(1));
        assertEquals("C", runLog.get(2));
        assertEquals("A", runLog.get(3));
        assertEquals("B", runLog.get(4));
        assertEquals("C", runLog.get(5));
        assertEquals("A", runLog.get(6));
        assertEquals("B", runLog.get(7));
        assertEquals("C", runLog.get(8));
    }

    @Test
    public void cpuMaintainsInstructionEndEventInExecutionQueue() throws Exception {
        for (String profile : CPU_PROFILES) {
            Env env = createEnv(profile);

            setVector(env.rom, 0xFFFC, PROG_PC);
            loadProgram(env, PROG_PC, 0xEA, 0xEA, 0x4C, 0x00, 0x02);

            assertEquals(1, env.cpu.getPendingExecutionEventCount());
            assertTrue(env.cpu.hasPendingInstructionEndEvent());

            env.emulator.startWithStepPhases(1, env.cpu, (step, manager, preCycle) -> true);
            assertEquals(1, env.cpu.getPendingExecutionEventCount());
            assertTrue(env.cpu.hasPendingInstructionEndEvent());
        }
    }

    @Test
    public void resetLeavesStackPointerAtFdByDefault() throws Exception {
        for (String profile : CPU_PROFILES) {
            Env env = createEnv(profile);
            setVector(env.rom, 0xFFFC, PROG_PC);
            loadProgram(env, PROG_PC, 0xEA); // NOP

            // Execute only RES.
            env.emulator.startWithStepPhases(1, env.cpu, (step, manager, preCycle) -> true);

            assertEquals(0xFD, env.cpu.getRegister().getS());
        }
    }

    @Test
    public void ldaUsesPendingCycleEventsBeforeInstructionEndEvent() throws Exception {
        for (String profile : CPU_PROFILES) {
            Env env = createEnv(profile);

            setVector(env.rom, 0xFFFC, PROG_PC);
            loadProgram(env, PROG_PC,
                    0xA9, 0x42,       // LDA #$42 (2 cycles)
                    0xEA,             // NOP
                    0x4C, 0x02, 0x02  // JMP $0202
            );

            // Execute RES; queue should now represent LDA micro-events.
            env.emulator.startWithStepPhases(1, env.cpu, (step, manager, preCycle) -> true);
            assertEquals(2, env.cpu.getPendingExecutionEventCount());
            assertTrue(env.cpu.hasPendingInFlightMicroEvent());

            // Execute one pending cycle, leaving final instruction-end event.
            env.emulator.startWithStepPhases(2, env.cpu, (step, manager, preCycle) -> true);
            assertEquals(1, env.cpu.getPendingExecutionEventCount());
            assertTrue(env.cpu.hasPendingInstructionEndEvent());
        }
    }

    @Test
    public void representativeMicroQueuedFamiliesShapeExecutionQueueByCycleEstimate() throws Exception {
        int[] representatives = new int[] {
                0xA9, // LDA
                0x85, // STA
                0xE6, // INC
                0xC6, // DEC
                0x0A, // ASL
                0x4A, // LSR
                0x2A, // ROL
                0x6A, // ROR
                0x09, // ORA
                0x29, // AND
                0x49, // EOR
                0x69, // ADC
                0xE9, // SBC
                0xC9, // CMP
                0x89, // BIT
                0xA2, // LDX
                0xA0, // LDY
                0x86, // STX
                0x84, // STY
                0xE0, // CPX
                0xC0  // CPY
        };

        for (int opcodeByte : representatives) {
            for (String profile : CPU_PROFILES) {
                Env env = createEnv(profile);
                env.cpu.setResetXOverride(0x00);
                env.cpu.setResetYOverride(0x00);
                setVector(env.rom, 0xFFFC, PROG_PC);
                prepareRepresentativeProgram(env, opcodeByte);

                env.emulator.startWithStepPhases(1, env.cpu, (step, manager, preCycle) -> true); // RES

                int expectedCycles = Cpu65c02CycleEstimator.predictInstructionCycles(
                        env.bus, env.cpu.getRegister(), Cpu65c02.OPCODE[opcodeByte & 0xFF], PROG_PC);
                assertEquals(expectedCycles, env.cpu.getPendingExecutionEventCount());
                if (expectedCycles > 1) {
                    assertTrue(env.cpu.hasPendingInFlightMicroEvent());
                } else {
                    assertTrue(env.cpu.hasPendingInstructionEndEvent());
                }

                env.emulator.startWithStepPhases(expectedCycles + 1L, env.cpu, (step, manager, preCycle) -> true);
                assertTrue(env.cpu.getPendingExecutionEventCount() >= 1);
            }
        }
    }

    @Test
    public void irqFromManagerIsSuppressedWhileInterruptDisableSet() throws Exception {
        for (String profile : CPU_PROFILES) {
            Env env = createEnv(profile);
            InterruptAtTickManager irq = new InterruptAtTickManager(env.cpu, Cpu65c02.INTERRUPT_IRQ, 30);
            PriorityQueue<HardwareManager> queue = new PriorityQueue<HardwareManager>();
            queue.add(env.cpu);
            queue.add(irq);
            env = new Env(env.bus, env.rom, env.cpu, new Emulator(queue, 0));

            setVector(env.rom, 0xFFFC, PROG_PC);
            setVector(env.rom, 0xFFFE, 0x0300);
            loadProgram(env, PROG_PC,
                    0xEA,
                    0x4C, 0x00, 0x02
            );
            loadProgram(env, 0x0300,
                    0xE6, 0x20,
                    0x40
            );
            env.bus.setByte(0x0020, 0x00);

            env.emulator.startWithStepPhases(140, env.cpu, (step, manager, preCycle) -> true);

            assertEquals(0, env.bus.getByte(0x0020));
        }
    }

    @Test
    public void nmiFromManagerIsTakenEvenWhenInterruptDisableSet() throws Exception {
        for (String profile : CPU_PROFILES) {
            Env env = createEnv(profile);
            InterruptAtTickManager nmi = new InterruptAtTickManager(env.cpu, Cpu65c02.INTERRUPT_NMI, 30);
            PriorityQueue<HardwareManager> queue = new PriorityQueue<HardwareManager>();
            queue.add(env.cpu);
            queue.add(nmi);
            env = new Env(env.bus, env.rom, env.cpu, new Emulator(queue, 0));

            setVector(env.rom, 0xFFFC, PROG_PC);
            setVector(env.rom, 0xFFFA, 0x0310);
            loadProgram(env, PROG_PC,
                    0xEA,
                    0x4C, 0x00, 0x02
            );
            loadProgram(env, 0x0310,
                    0xE6, 0x21,
                    0x40
            );
            env.bus.setByte(0x0021, 0x00);

            env.emulator.startWithStepPhases(140, env.cpu, (step, manager, preCycle) -> true);

            assertEquals(1, env.bus.getByte(0x0021));
        }
    }

    @Test
    public void pendingIrqStartsAfterCurrentInstructionCompletes() throws Exception {
        for (String profile : CPU_PROFILES) {
            Env env = createEnv(profile);

            setVector(env.rom, 0xFFFC, PROG_PC);
            setVector(env.rom, 0xFFFE, 0x0300);

            loadProgram(env, PROG_PC,
                    0x58,             // CLI
                    0xE6, 0x30,       // INC $30
                    0x4C, 0x01, 0x02  // JMP $0201
            );

            loadProgram(env, 0x0300,
                    0xE6, 0x20,       // INC $20
                    0x40              // RTI
            );

            env.bus.setByte(0x0020, 0x00);
            env.bus.setByte(0x0030, 0x00);

            env.emulator.startWithStepPhases(200, env.cpu, (step, manager, preCycle) -> {
                if (preCycle && step == 3) {
                    env.cpu.setInterruptPending(Cpu65c02.INTERRUPT_IRQ);
                }
                if (!preCycle && env.bus.getByte(0x0030) == 1 && env.bus.getByte(0x0020) == 1) {
                    return false;
                }
                return true;
            });

            assertEquals(1, env.bus.getByte(0x0030));
            assertEquals(1, env.bus.getByte(0x0020));
        }
    }
}
