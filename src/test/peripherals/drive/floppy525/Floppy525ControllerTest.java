package test.peripherals.drive.floppy525;

import core.emulator.VirtualMachineProperties;
import core.memory.memory8.MemoryBusIIe.SwitchSet8;
import org.junit.Test;
import peripherals.drive.floppy525.Floppy525Controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Floppy525ControllerTest {

    private static final int TRACK_TOTAL = 35;
    private static final int TRACK_BYTES = 416 * 16;
    private static final int INITIAL_TRACK = 34;
    private static final int FIRST_WRITTEN_BYTE_OFFSET = INITIAL_TRACK * TRACK_BYTES + 1;
    private static final int PAYLOAD_SIZE = 768;
    private static final int SLOT_ROM_SIZE = 0x100;

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
        byte[] prefix = new byte[] {
                (byte) 0xa2, 0x20,
                (byte) 0xa0, 0x00,
                (byte) 0xa2, 0x03,
                (byte) 0x86, 0x3c,
                (byte) 0x4c, 0x08, (byte) 0xc6
        };
        System.arraycopy(prefix, 0, rom, 0, prefix.length);
        byte[] label = "EVER2E P6 TEST ROM".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(label, 0, rom, 0x10, label.length);
        rom[0xff] = 0x00;
        return rom;
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
