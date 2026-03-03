package test.device.display;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FloatingBusAddressResolutionTest {

    private static final int H_SIZE = 65;
    private static final int V_SIZE = 262;

    /**
     * Port of MAME-style floating-bus source address math for a given
     * retrace coordinate (beam x/y) and display soft-switch state.
     */
    private static int mameAddressForRetrace(
            int hScan,
            int vScan,
            boolean text,
            boolean mixed,
            boolean hires,
            boolean page2,
            boolean store80
    ) {
        int hClock = (hScan + 48) % H_SIZE;
        int hState = 0x18 + hClock;
        if (hClock >= 40)
            hState -= 1;

        int h0 = (hState >> 0) & 1;
        int h1 = (hState >> 1) & 1;
        int h2 = (hState >> 2) & 1;
        int h3 = (hState >> 3) & 1;
        int h4 = (hState >> 4) & 1;
        int h5 = (hState >> 5) & 1;

        int vLine = vScan + 192;
        if (vLine >= 256)
            vLine -= V_SIZE;
        int vState = 0x100 + vLine;

        int vA = (vState >> 0) & 1;
        int vB = (vState >> 1) & 1;
        int vC = (vState >> 2) & 1;
        int v0 = (vState >> 3) & 1;
        int v1 = (vState >> 4) & 1;
        int v2 = (vState >> 5) & 1;
        int v3 = (vState >> 6) & 1;
        int v4 = (vState >> 7) & 1;

        int hiresEff = (hires && !text) ? 1 : 0;
        if (hiresEff == 1 && mixed && ((v4 & v2) != 0))
            hiresEff = 0;

        int addend0 = 0x68;
        int addend1 = (h5 << 5) | (h4 << 4) | (h3 << 3);
        int addend2 = (v4 << 6) | (v3 << 5) | (v4 << 4) | (v3 << 3);
        int summed = (addend0 + addend1 + addend2) & (0x0F << 3);

        int p2 = page2 ? 1 : 0;
        int s80 = store80 ? 1 : 0;

        int addr = 0;
        addr |= h0 << 0;
        addr |= h1 << 1;
        addr |= h2 << 2;
        addr |= summed;
        addr |= v0 << 7;
        addr |= v1 << 8;
        addr |= v2 << 9;
        addr |= (hiresEff == 1 ? vA : (1 ^ (p2 & (1 ^ s80)))) << 10;
        addr |= (hiresEff == 1 ? vB : (p2 & (1 ^ s80))) << 11;

        if (hiresEff == 1) {
            addr |= vC << 12;
            addr |= (1 ^ (p2 & (1 ^ s80))) << 13;
            addr |= (p2 & (1 ^ s80)) << 14;
        }

        return addr & 0xFFFF;
    }

    @Test
    public void resolvesGoldenVectorsForLoresAndHiresPages() {
        assertEquals(0x0467, mameAddressForRetrace(0, 0, true, false, false, false, false));
        assertEquals(0x0407, mameAddressForRetrace(24, 70, true, false, false, false, false));
        assertEquals(0x041F, mameAddressForRetrace(57, 0, true, false, false, false, false));
        assertEquals(0x07BE, mameAddressForRetrace(64, 261, true, false, false, false, false));

        assertEquals(0x2067, mameAddressForRetrace(0, 0, false, false, true, false, false));
        assertEquals(0x2007, mameAddressForRetrace(24, 70, false, false, true, false, false));
        assertEquals(0x201F, mameAddressForRetrace(57, 0, false, false, true, false, false));
        assertEquals(0x3FBE, mameAddressForRetrace(64, 261, false, false, true, false, false));

        assertEquals(0x4067, mameAddressForRetrace(0, 0, false, false, true, true, false));
        assertEquals(0x4007, mameAddressForRetrace(24, 70, false, false, true, true, false));
        assertEquals(0x401F, mameAddressForRetrace(57, 0, false, false, true, true, false));
        assertEquals(0x5FBE, mameAddressForRetrace(64, 261, false, false, true, true, false));
    }

    @Test
    public void mixedModeFallsBackFromHiresInTextBand() {
        // v=120 lands in the mixed/text band for this mapping.
        assertEquals(0x2B79, mameAddressForRetrace(10, 120, false, true, true, false, false));
        // v=40 remains in hi-res region.
        assertEquals(0x06F1, mameAddressForRetrace(10, 40, false, true, true, false, false));
    }
}
