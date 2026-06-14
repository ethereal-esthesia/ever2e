package test.peripherals.drive.floppy525;

import core.cpu.cpu8.Cpu65c02;
import core.emulator.HardwareManager;
import core.emulator.machine.Emulator;
import core.emulator.VirtualMachineProperties;
import core.memory.memory8.Memory8;
import core.memory.memory8.MemoryBusIIe;
import core.memory.memory8.MemoryBusIIe.SwitchSet8;
import org.junit.Test;
import peripherals.drive.floppy525.Floppy525Controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.PriorityQueue;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Floppy525ControllerTest {

    private static final int TRACK_TOTAL = 35;
    private static final int TRACK_BYTES = 416 * 16;
    private static final int INITIAL_TRACK = 34;
    private static final int FIRST_WRITTEN_BYTE_OFFSET = INITIAL_TRACK * TRACK_BYTES + 1;
    private static final int PAYLOAD_SIZE = 768;
    private static final int SLOT_ROM_SIZE = 0x100;
    private static final int MEMORY_SIZE = 0x20000;
    private static final int SYSTEM_ROM_SIZE = 0x4000;
    private static final int PAYLOAD_ADDR = 0x0800;
    private static final int PAYLOAD_LOOP = PAYLOAD_ADDR + 0x0A;
    private static final int BOOT_MARK_ADDR = 0x0400;
    private static final int BOOT_SYNC_BYTES = 32;
    private static final byte[] BOOT_MAGIC = new byte[] { (byte) 0xe2, (byte) 0xb0, (byte) 0xb1, (byte) 0xb2 };

    @Test
    public void writesDeterministicRandomBytesThroughDiskSwitches() throws Exception {
        Path dir = Files.createTempDirectory("ever2e-floppy525-random-");
        try {
            Path nib = dir.resolve("scratch.nib");
            Path rom = dir.resolve("dummy.rom");
            Path p6rom = dir.resolve("diskii-p6-test.rom");
            Path emu = dir.resolve("scratch.emu");

            Files.write(nib, filled(TRACK_TOTAL * TRACK_BYTES, (byte) 0xff));
            Files.write(rom, new byte[] { 0 });
            Files.write(p6rom, buildDiskIIP6TestRom());
            Files.writeString(emu,
                    "machine.layout=APPLE_IIE\n" +
                    "binary.file=dummy.rom\n" +
                    "address.start=0xC000\n" +
                    "machine.layout.slot.6.rom.file=" + p6rom.getFileName() + "\n" +
                    "machine.layout.slot.6.drive.1.file=" + nib + "\n",
                    StandardCharsets.UTF_8);

            Floppy525Controller controller =
                    new Floppy525Controller(6, 1, new VirtualMachineProperties(emu.toString()));
            SwitchSet8 switches = controller.getSwitchSet();
            byte[] payload = deterministicPayload();
            assertArrayEquals(buildDiskIIP6TestRom(), controller.getRom256b());

            switches.writeMem(0x09, 0x00); // drive on
            switches.writeMem(0x0f, 0x00); // write mode

            for (byte value : payload) {
                switches.writeMem(0x0d, value & 0xff);
                switches.writeMem(0x0c, 0x00);
                controller.cycle();
            }

            switches.writeMem(0x0e, 0x00); // read mode
            switches.writeMem(0x08, 0x00); // drive off
            for (int i = 0; i < ((0x40000 >> 3) + 2); i++)
                controller.cycle();

            byte[] image = Files.readAllBytes(nib);
            assertEquals(TRACK_TOTAL * TRACK_BYTES, image.length);
            assertArrayEquals(payload, Arrays.copyOfRange(
                    image,
                    FIRST_WRITTEN_BYTE_OFFSET,
                    FIRST_WRITTEN_BYTE_OFFSET + payload.length));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void bootsGeneratedCustomDiskThroughP6Rom() throws Exception {
        Path dir = Files.createTempDirectory("ever2e-floppy525-boot-");
        try {
            Path nib = dir.resolve("custom-boot.nib");
            Path rom = dir.resolve("dummy.rom");
            Path p6rom = dir.resolve("diskii-p6-test.rom");
            Path emu = dir.resolve("boot.emu");

            Files.write(nib, buildCustomBootNib());
            Files.write(rom, new byte[] { 0 });
            Files.write(p6rom, buildDiskIIP6TestRom());
            Files.writeString(emu,
                    "machine.layout=APPLE_IIE\n" +
                    "binary.file=dummy.rom\n" +
                    "address.start=0xC000\n" +
                    "machine.layout.slot.6.rom.file=" + p6rom.getFileName() + "\n" +
                    "machine.layout.slot.6.drive.1.file=" + nib + "\n",
                    StandardCharsets.UTF_8);

            Floppy525Controller controller =
                    new Floppy525Controller(6, 1, new VirtualMachineProperties(emu.toString()));
            assertArrayEquals(buildDiskIIP6TestRom(), controller.getRom256b());

            byte[] systemRom = filled(SYSTEM_ROM_SIZE, (byte) 0xea);
            systemRom[0x3ffc] = 0x00;
            systemRom[0x3ffd] = (byte) 0xc6;

            MemoryBusIIe bus = new MemoryBusIIe(new Memory8(MEMORY_SIZE), systemRom);
            bus.setSlot(6, controller);
            bus.setSlotRom(6, controller.getRom256b());

            Cpu65c02 cpu = new Cpu65c02(bus, 1);
            PriorityQueue<HardwareManager> queue = new PriorityQueue<HardwareManager>();
            queue.add(cpu);
            queue.add(controller);
            Emulator emulator = new Emulator(queue, 0);

            boolean booted = false;
            for (int chunk = 0; chunk < 300 && !booted; chunk++) {
                emulator.start(1000, cpu);
                booted = bus.peekByteNoSideEffects(BOOT_MARK_ADDR) == 0x42 &&
                        bus.peekByteNoSideEffects(BOOT_MARK_ADDR + 1) == 0xc8;
            }

            assertTrue("generated P6 boot did not reach payload loop", booted);
            byte[] payload = buildBootPayload();
            for (int i = 0; i < payload.length; i++) {
                assertEquals("payload byte at " + String.format("$%04X", PAYLOAD_ADDR + i),
                        payload[i] & 0xff,
                        bus.peekByteNoSideEffects(PAYLOAD_ADDR + i));
            }
            assertEquals(0x4c, bus.peekByteNoSideEffects(PAYLOAD_LOOP));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void rejectsMissingConfiguredDiskImageBeforeInstallingSlot() throws Exception {
        Path dir = Files.createTempDirectory("ever2e-floppy525-missing-");
        try {
            Path rom = dir.resolve("dummy.rom");
            Path p6rom = dir.resolve("diskii-p6-test.rom");
            Path emu = dir.resolve("missing.emu");

            Files.write(rom, new byte[] { 0 });
            Files.write(p6rom, buildDiskIIP6TestRom());
            Files.writeString(emu,
                    "machine.layout=APPLE_IIE\n" +
                    "binary.file=dummy.rom\n" +
                    "address.start=0xC000\n" +
                    "machine.layout.slot.6.rom.file=" + p6rom.getFileName() + "\n" +
                    "machine.layout.slot.6.drive.1.file=missing.nib\n",
                    StandardCharsets.UTF_8);

            try {
                new Floppy525Controller(6, 1, new VirtualMachineProperties(emu.toString()));
                fail("missing disk image should reject the controller during slot installation");
            } catch (java.io.FileNotFoundException expected) {
                assertTrue(expected.getMessage().contains("Drive 1 disk image not found"));
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void rejectsDiskControllerWithNoConfiguredMedia() throws Exception {
        Path dir = Files.createTempDirectory("ever2e-floppy525-empty-");
        try {
            Path rom = dir.resolve("dummy.rom");
            Path p6rom = dir.resolve("diskii-p6-test.rom");
            Path emu = dir.resolve("empty.emu");

            Files.write(rom, new byte[] { 0 });
            Files.write(p6rom, buildDiskIIP6TestRom());
            Files.writeString(emu,
                    "machine.layout=APPLE_IIE\n" +
                    "binary.file=dummy.rom\n" +
                    "address.start=0xC000\n" +
                    "machine.layout.slot.6.rom.file=" + p6rom.getFileName() + "\n" +
                    "machine.layout.slot.6.drive.1.file=\n" +
                    "machine.layout.slot.6.drive.2.file=\n",
                    StandardCharsets.UTF_8);

            try {
                new Floppy525Controller(6, 1, new VirtualMachineProperties(emu.toString()));
                fail("empty disk configuration should reject the controller during slot installation");
            } catch (java.io.FileNotFoundException expected) {
                assertTrue(expected.getMessage().contains("No disk images configured"));
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    private static byte[] deterministicPayload() {
        byte[] payload = new byte[PAYLOAD_SIZE];
        long seed = 0x5eed1234L;
        for (int i = 0; i < payload.length; i++) {
            seed = (1103515245L * seed + 12345) & 0x7fffffffL;
            payload[i] = (byte) ((seed >> 8) & 0xff);
        }
        return payload;
    }

    private static byte[] filled(int size, byte value) {
        byte[] data = new byte[size];
        Arrays.fill(data, value);
        return data;
    }

    private static byte[] buildDiskIIP6TestRom() {
        byte[] rom = filled(SLOT_ROM_SIZE, (byte) 0xea);
        byte[] loader = new byte[] {
                (byte) 0xa2, 0x20, (byte) 0xa0, 0x00, (byte) 0xa2, 0x03, (byte) 0x86, 0x3c,
                (byte) 0xa2, 0x60, (byte) 0xbd, (byte) 0x89, (byte) 0xc0, (byte) 0xbd, (byte) 0x8e, (byte) 0xc0,
                0x20, 0x49, (byte) 0xc6, (byte) 0xc9, (byte) 0xe2, (byte) 0xd0, (byte) 0xf9,
                0x20, 0x49, (byte) 0xc6, (byte) 0xc9, (byte) 0xb0, (byte) 0xd0, (byte) 0xf2,
                0x20, 0x49, (byte) 0xc6, (byte) 0xc9, (byte) 0xb1, (byte) 0xd0, (byte) 0xeb,
                0x20, 0x49, (byte) 0xc6, (byte) 0xc9, (byte) 0xb2, (byte) 0xd0, (byte) 0xe4,
                (byte) 0xa0, 0x00, 0x20, 0x49, (byte) 0xc6, 0x29, 0x0f, 0x0a, 0x0a, 0x0a,
                0x0a, (byte) 0x85, 0x3d, 0x20, 0x49, (byte) 0xc6, 0x29, 0x0f, 0x05, 0x3d,
                (byte) 0x99, 0x00, 0x08, (byte) 0xc8, (byte) 0xd0, (byte) 0xe8, 0x4c, 0x00, 0x08,
                (byte) 0xbd, (byte) 0x8c, (byte) 0xc0, 0x10, (byte) 0xfb, 0x60
        };
        System.arraycopy(loader, 0, rom, 0, loader.length);
        rom[0xff] = 0x00;
        return rom;
    }

    private static byte[] buildCustomBootNib() {
        byte[] image = filled(TRACK_TOTAL * TRACK_BYTES, (byte) 0xff);
        int pos = INITIAL_TRACK * TRACK_BYTES;
        pos += BOOT_SYNC_BYTES;
        System.arraycopy(BOOT_MAGIC, 0, image, pos, BOOT_MAGIC.length);
        pos += BOOT_MAGIC.length;
        byte[] encoded = encodePayload(buildBootPayload());
        System.arraycopy(encoded, 0, image, pos, encoded.length);
        return image;
    }

    private static byte[] buildBootPayload() {
        byte[] payload = filled(0x100, (byte) 0xea);
        byte[] code = new byte[] {
                (byte) 0xa9, 0x42,
                (byte) 0x8d, BOOT_MARK_ADDR & 0xff, (BOOT_MARK_ADDR >> 8) & 0xff,
                (byte) 0xa9, (byte) 0xc8,
                (byte) 0x8d, (BOOT_MARK_ADDR + 1) & 0xff, ((BOOT_MARK_ADDR + 1) >> 8) & 0xff,
                0x4c, PAYLOAD_LOOP & 0xff, (PAYLOAD_LOOP >> 8) & 0xff
        };
        System.arraycopy(code, 0, payload, 0, code.length);
        return payload;
    }

    private static byte[] encodePayload(byte[] payload) {
        byte[] encoded = new byte[payload.length * 2];
        for (int i = 0; i < payload.length; i++) {
            int value = payload[i] & 0xff;
            encoded[i * 2] = (byte) (0xa0 | (value >> 4));
            encoded[i * 2 + 1] = (byte) (0xa0 | (value & 0x0f));
        }
        return encoded;
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path))
            return;
        try (var stream = Files.walk(path)) {
            stream
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
