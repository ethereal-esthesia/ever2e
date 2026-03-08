package test.cpu;

import core.cpu.cpu8.Cpu65c02;
import core.cpu.cpu8.Cpu65c02Cmd;
import core.cpu.cpu8.Cpu65c02.OpcodeMnemonic;
import core.emulator.HardwareManager;
import core.emulator.machine.Emulator;
import core.exception.HardwareException;
import core.memory.memory8.Memory8;
import core.memory.memory8.MemoryBusIIe;
import device.display.VideoSignalSource;
import org.junit.Test;

import java.util.PriorityQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class EmulatorSchedulerIntegrationTest {

    private static final int MEM_SIZE = 0x20000;
    private static final int ROM_SIZE = 0x4000;
    private static final int PROG_PC = 0x0200;
    private static final String[] CPU_PROFILES = new String[] { "wdc", "cmd" };

    private static final class TestVideoManager extends HardwareManager implements VideoSignalSource {
        private int lastRead = 0x40;
        private boolean vbl;
        private int vScan;

        TestVideoManager() {
            super(1);
        }

        @Override
        public void coldReset() {
            resetCycleCount();
            lastRead = 0x40;
            vbl = false;
            vScan = 0;
        }

        @Override
        public void cycle() {
            lastRead = (lastRead + 1) & 0xFF;
            vScan = (vScan + 1) & 0x1FF;
            vbl = (vScan % 9) < 3;
            incSleepCycles(1);
        }

        @Override
        public int getLastRead() {
            return lastRead;
        }

        @Override
        public int getHScan() {
            return vScan & 0x3F;
        }

        @Override
        public boolean isVbl() {
            return vbl;
        }

        @Override
        public int getVScan() {
            return vScan;
        }
    }

    private static final class InterruptInjectorManager extends HardwareManager {
        private Cpu65c02 cpu;
        private int ticks;
        private boolean nmiSent;
        private boolean irqSent;

        InterruptInjectorManager(Cpu65c02 cpu) {
            super(1);
            this.cpu = cpu;
        }

        void setCpu(Cpu65c02 cpu) {
            this.cpu = cpu;
        }

        @Override
        public void coldReset() {
            resetCycleCount();
            ticks = 0;
            nmiSent = false;
            irqSent = false;
        }

        @Override
        public void cycle() {
            ticks++;
            if (!nmiSent && ticks >= 40) {
                cpu.setInterruptPending(Cpu65c02.INTERRUPT_NMI);
                nmiSent = true;
            }
            if (!irqSent && ticks >= 120) {
                cpu.setInterruptPending(Cpu65c02.INTERRUPT_IRQ);
                irqSent = true;
            }
            incSleepCycles(1);
        }
    }

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

    @Test
    public void schedulerDeliversNmiAndIrqFromSeparateManager() throws Exception {
        for (String profile : CPU_PROFILES) {
            InterruptInjectorManager injector = new InterruptInjectorManager(null);
            Env env = createEnv(profile, injector);
            injector.setCpu(env.cpu);

            setVector(env.rom, 0xFFFC, PROG_PC);
            setVector(env.rom, 0xFFFA, 0x0300);
            setVector(env.rom, 0xFFFE, 0x0310);

            loadProgram(env, PROG_PC,
                    0x58,             // CLI
                    0xEA,             // NOP
                    0x4C, 0x01, 0x02  // JMP $0201
            );

            loadProgram(env, 0x0300,
                    0xE6, 0x10,       // INC $10
                    0x40              // RTI
            );

            loadProgram(env, 0x0310,
                    0xE6, 0x11,       // INC $11
                    0x40              // RTI
            );

            env.bus.setByte(0x0010, 0x00);
            env.bus.setByte(0x0011, 0x00);

            env.emulator.startWithStepPhases(220, env.cpu, (step, manager, preCycle) -> true);

            assertEquals(1, env.bus.getByte(0x0010));
            assertEquals(1, env.bus.getByte(0x0011));
            assertTrue(env.cpu.getOpcode().getMnemonic() != OpcodeMnemonic.RES);
        }
    }

    @Test
    public void schedulerSeesAcyclicalFloatingBusReads() throws Exception {
        for (String profile : CPU_PROFILES) {
            TestVideoManager video = new TestVideoManager();
            Env env = createEnv(profile, video);
            env.bus.setDisplay(video);

            setVector(env.rom, 0xFFFC, PROG_PC);

            env.bus.setText(true);

            loadProgram(env, PROG_PC,
                    0xAD, 0x50, 0xC0, // LDA $C050 (floating bus read + clear TEXT)
                    0x8D, 0x20, 0x02, // STA $0220
                    0xAD, 0x50, 0xC0, // LDA $C050 again
                    0x8D, 0x21, 0x02, // STA $0221
                    0xAD, 0x19, 0xC0, // LDA $C019 (VBLBAR)
                    0x8D, 0x22, 0x02, // STA $0222
                    0x4C, 0x12, 0x02  // JMP $0212 (self loop)
            );

            env.emulator.startWithStepPhases(40, env.cpu, (step, manager, preCycle) -> true);

            int sample0 = env.bus.getByte(0x0220);
            int sample1 = env.bus.getByte(0x0221);
            int vblBar = env.bus.getByte(0x0222);

            assertNotEquals(sample0, sample1);
            assertTrue(sample0 >= 0x40 && sample1 >= 0x40);
            assertTrue(vblBar == 0x00 || vblBar == 0x80);
            assertTrue(!env.bus.isText());
        }
    }
}
