package test.cpu;

import core.emulator.machine.machine8.Emulator8Coordinator;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class Emulator8CoordinatorDisplayValuesTest {

    private static int invokeInt(String methodName, int value) throws Exception {
        Method method = Emulator8Coordinator.class.getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, value);
    }

    @Test
    public void derivedValuesMatchKnownCheckpoints() throws Exception {
        // frameCycle, expectedH, expectedV, expectedVbl, expectedX, expectedY, expectedSc, expectedTraceFc
        int[][] cases = new int[][] {
                {0, 0, 0, 1, 0, 0, 17029, 0},
                {64, 64, 0, 1, 896, 0, 16965, 64},
                {65, 0, 1, 1, 0, 1, 16964, 65},
                {4549, 64, 69, 1, 896, 69, 12480, 4549},
                {4550, 0, 70, 0, 0, 70, 12479, 4550},
                {12490, 10, 192, 0, 140, 192, 4539, 12490},
                {17029, 64, 261, 0, 896, 261, 0, 17029},
                {17030, 0, 0, 1, 0, 0, 17029, 0},
                {51132, 42, 0, 1, 588, 0, 16987, 42},
        };

        for (int[] c : cases) {
            int frameCycle = c[0];
            assertEquals("hscan for fc=" + frameCycle, c[1], invokeInt("displayHScan", frameCycle));
            assertEquals("vscan for fc=" + frameCycle, c[2], invokeInt("displayVScan", frameCycle));
            assertEquals("vbl for fc=" + frameCycle, c[3], invokeInt("displayVblBit", frameCycle));
            assertEquals("scan x for fc=" + frameCycle, c[4], invokeInt("displayScanX", frameCycle));
            assertEquals("scan y for fc=" + frameCycle, c[5], invokeInt("displayScanY", frameCycle));
            assertEquals("scan cycles desc for fc=" + frameCycle, c[6], invokeInt("displayScanCyclesDesc", frameCycle));
            assertEquals("trace frame cycle for fc=" + frameCycle, c[7], invokeInt("traceFrameCycle", frameCycle));
        }
    }

    @Test
    public void derivedValuesAreInternallyConsistentAcrossFrame() throws Exception {
        final int frameLength = 17030;
        for (int fc = 0; fc < frameLength; fc++) {
            int h = invokeInt("displayHScan", fc);
            int v = invokeInt("displayVScan", fc);
            int vbl = invokeInt("displayVblBit", fc);
            int x = invokeInt("displayScanX", fc);
            int y = invokeInt("displayScanY", fc);
            int sc = invokeInt("displayScanCyclesDesc", fc);
            int traceFc = invokeInt("traceFrameCycle", fc);

            assertEquals("h relation fc=" + fc, fc % 65, h);
            assertEquals("v relation fc=" + fc, (fc / 65) % 262, v);
            assertEquals("vbl relation fc=" + fc, v < 70 ? 1 : 0, vbl);
            assertEquals("x relation fc=" + fc, h * 14, x);
            assertEquals("y relation fc=" + fc, v, y);
            assertEquals("sc relation fc=" + fc, 17029 - fc, sc);
            assertEquals("trace fc relation fc=" + fc, fc, traceFc);
        }
    }
}
