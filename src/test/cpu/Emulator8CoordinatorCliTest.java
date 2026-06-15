package test.cpu;

import core.emulator.machine.machine8.Emulator8Coordinator;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Emulator8CoordinatorCliTest {

    private static boolean isPropertyAssignmentArg(String value) throws Exception {
        Method method = Emulator8Coordinator.class.getDeclaredMethod("isPropertyAssignmentArg", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, value);
    }

    @Test
    public void bareNameValueArgumentsArePropertyOverrides() throws Exception {
        assertTrue(isPropertyAssignmentArg("machine.layout.slot.6.rom.file=~/slot6.rom"));
        assertTrue(isPropertyAssignmentArg("machine.cpu.mult=2"));
    }

    @Test
    public void optionsAndProfilesAreNotBarePropertyOverrides() throws Exception {
        assertFalse(isPropertyAssignmentArg("--drive1=disk.nib"));
        assertFalse(isPropertyAssignmentArg("ROMS/Apple2e.emu"));
        assertFalse(isPropertyAssignmentArg("=missing-name"));
    }
}
