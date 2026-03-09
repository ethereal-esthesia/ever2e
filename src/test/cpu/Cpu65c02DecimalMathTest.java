package test.cpu;

import core.cpu.cpu8.Cpu65c02;
import core.cpu.cpu8.Register;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class Cpu65c02DecimalMathTest {

    private static final int FLAG_N = 0x80;
    private static final int FLAG_V = 0x40;
    private static final int FLAG_R = 0x20;
    private static final int FLAG_B = 0x10;
    private static final int FLAG_D = 0x08;
    private static final int FLAG_I = 0x04;
    private static final int FLAG_Z = 0x02;
    private static final int FLAG_C = 0x01;

    private static final class Expected {
        final int a;
        final int p;

        Expected(int a, int p) {
            this.a = a & 0xFF;
            this.p = p & 0xFF;
        }
    }

    private static Method adcMethod;
    private static Method sbcMethod;

    private static Method getAdcMethod() throws Exception {
        if (adcMethod == null) {
            adcMethod = Cpu65c02.class.getDeclaredMethod("applyAdcDecimal", Register.class, int.class);
            adcMethod.setAccessible(true);
        }
        return adcMethod;
    }

    private static Method getSbcMethod() throws Exception {
        if (sbcMethod == null) {
            sbcMethod = Cpu65c02.class.getDeclaredMethod("applySbcDecimal", Register.class, int.class);
            sbcMethod.setAccessible(true);
        }
        return sbcMethod;
    }

    private static Expected refAdcDecimal(int a, int val, int pIn) {
        int carryIn = (pIn & FLAG_C) != 0 ? 1 : 0;
        int p = pIn & ~(FLAG_N | FLAG_V | FLAG_Z | FLAG_C);

        int al = (a & 0x0F) + (val & 0x0F) + carryIn;
        if (al > 9)
            al += 6;
        int ah = (a >> 4) + (val >> 4) + (al > 0x0F ? 1 : 0);

        if (((a + val + carryIn) & 0xFF) == 0)
            p |= FLAG_Z;
        else if ((ah & 0x08) != 0)
            p |= FLAG_N;
        if (((~(a ^ val)) & (a ^ (ah << 4)) & 0x80) != 0)
            p |= FLAG_V;

        if (ah > 9)
            ah += 6;
        if (ah > 15)
            p |= FLAG_C;

        int outA = ((ah << 4) | (al & 0x0F)) & 0xFF;
        return new Expected(outA, p);
    }

    private static Expected refSbcDecimal(int a, int val, int pIn) {
        int borrowIn = (pIn & FLAG_C) != 0 ? 0 : 1;
        int p = pIn & ~(FLAG_N | FLAG_V | FLAG_Z | FLAG_C);

        int diff = a - val - borrowIn;
        int al = (a & 0x0F) - (val & 0x0F) - borrowIn;
        if (al < 0)
            al -= 6;
        int ah = (a >> 4) - (val >> 4) - (al < 0 ? 1 : 0);

        if ((diff & 0xFF) == 0)
            p |= FLAG_Z;
        else if ((diff & 0x80) != 0)
            p |= FLAG_N;
        if (((a ^ val) & (a ^ diff) & 0x80) != 0)
            p |= FLAG_V;
        if ((diff & 0xFF00) == 0)
            p |= FLAG_C;
        if (ah < 0)
            ah -= 6;

        int outA = ((ah << 4) | (al & 0x0F)) & 0xFF;
        return new Expected(outA, p);
    }

    @Test
    public void adcDecimalMatchesReferenceForAllInputs() throws Exception {
        Register reg = new Register();
        Method adc = getAdcMethod();
        int baseP = FLAG_R | FLAG_B | FLAG_D | FLAG_I;

        for (int a = 0; a <= 0xFF; a++) {
            for (int val = 0; val <= 0xFF; val++) {
                for (int carry = 0; carry <= 1; carry++) {
                    int pIn = baseP | carry;
                    reg.setA(a);
                    reg.setP(pIn);
                    adc.invoke(null, reg, val);

                    Expected expected = refAdcDecimal(a, val, pIn);
                    assertEquals(String.format("ADC A mismatch a=%02X val=%02X c=%d", a, val, carry),
                            expected.a, reg.getA());
                    assertEquals(String.format("ADC P mismatch a=%02X val=%02X c=%d", a, val, carry),
                            expected.p, reg.getP());
                }
            }
        }
    }

    @Test
    public void sbcDecimalMatchesReferenceForAllInputs() throws Exception {
        Register reg = new Register();
        Method sbc = getSbcMethod();
        int baseP = FLAG_R | FLAG_B | FLAG_D | FLAG_I;

        for (int a = 0; a <= 0xFF; a++) {
            for (int val = 0; val <= 0xFF; val++) {
                for (int carry = 0; carry <= 1; carry++) {
                    int pIn = baseP | carry;
                    reg.setA(a);
                    reg.setP(pIn);
                    sbc.invoke(null, reg, val);

                    Expected expected = refSbcDecimal(a, val, pIn);
                    assertEquals(String.format("SBC A mismatch a=%02X val=%02X c=%d", a, val, carry),
                            expected.a, reg.getA());
                    assertEquals(String.format("SBC P mismatch a=%02X val=%02X c=%d", a, val, carry),
                            expected.p, reg.getP());
                }
            }
        }
    }
}
