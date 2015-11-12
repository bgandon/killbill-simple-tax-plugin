/*
 * Copyright 2015 Benjamin Gandon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.killbill.billing.plugin.simpletax.internal;

import static org.killbill.billing.test.helpers.TestUtil.shortIdentityToString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for {@link VATIN}s.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestVATIN {

    private static final String FR_TEST0_NUM = "FR12000000000";
    private static final String FR_TEST1_NUM = "FR23111111111";
    private static final String FR_TEST2_NUM = "FR34222222222";
    private static final String FR_TEST3 = "FR45333333333";
    private static final String FR_TEST4 = "FR56444444444";
    private static final String FR_TEST5 = "FR67555555555";
    private static final String FR_TEST6 = "FR78666666666";
    private static final String FR_TEST7 = "FR89777777777";
    private static final String FR_TEST8_NUM = "FR03888888888";
    private static final String FR_TEST9_NUM = "FR14999999999";

    private static final VATIN FR_TEST0 = new VATIN(FR_TEST0_NUM);
    private static final VATIN FR_TEST1 = new VATIN(FR_TEST1_NUM);
    private static final VATIN FR_TEST2 = new VATIN(FR_TEST2_NUM);
    private static final VATIN FR_TEST8 = new VATIN(FR_TEST8_NUM);
    private static final VATIN FR_TEST9 = new VATIN(FR_TEST9_NUM);

    private static final Object[][] ILLEGAL_VATINS = { { null }, { "" }, { " " }, { "\t" }, { "\r" }, { "\n" },
        { "FR1plop678912" }, { "FR 12 3456678" }, { "FR123456     " },//
        // Right numbers with illegal characters:
        { "FR1200000000." }, { "FR23111111111\n" }, { "\tFR34222222222" },//
        // Wrong checksums:
        { "FR12345678901" }, { "FR01234567890" }, { "FR90123456789" },
            // Loosely validated forms ('I' and 'O' are forbidden)
        { "FRI1234567890" }, { "FR0O123456789" }, { "FRIA123456789" }, { "FRAI123456789" } };
    private static final Object[][] LEGAL_VATINS = {//
        { "FR32123456789" },// 127275040,32989690721649 -> 32
        { "FR09111222333" },// 114662199,09278350515464 -> 09
        { "FR05987654321" },// 1018200331,05154639175258 -> 05
        { "FR59333222111" },// 343527949,60824742268041 -> 59
        { FR_TEST0_NUM },// 114547537,23711340206186 -> 23
        { FR_TEST1_NUM },// 114547537,23711340206186 -> 23
        { FR_TEST2_NUM },// 229095074,35051546391753 -> 34
        { FR_TEST3 }, { FR_TEST4 }, { FR_TEST5 }, { FR_TEST6 },//
        { FR_TEST7 }, { FR_TEST8_NUM }, { FR_TEST9_NUM },//
        // Loosely validated forms
        { "FRA1234567890" }, { "FR0A123456789" }, { "FRAA123456789" } };

    @DataProvider(name = "illegalVATINs")
    public static Object[][] illegalVATINs() {
        return ILLEGAL_VATINS;
    }

    @DataProvider(name = "legalVATINs")
    public static Object[][] legalVATINs() {
        return LEGAL_VATINS;
    }

    @Test(groups = "fast", dataProvider = "illegalVATINs", expectedExceptions = IllegalArgumentException.class)
    public void shouldNotConstructInvalidVATINs(String invalidVATIN) {
        // Expect exception
        new VATIN(invalidVATIN);
    }

    @Test(groups = "fast", dataProvider = "legalVATINs")
    public void shouldConstructValidVATINs(String legalVATIN) {
        // When
        VATIN vatin = new VATIN(legalVATIN);

        // Then
        assertNotNull(vatin);
        assertEquals(vatin.getNumber(), legalVATIN);
    }

    @Test(groups = "fast")
    public void shouldEnforceEquality() {
        // Expect
        assertFalse(FR_TEST0.equals(null));
        assertFalse(FR_TEST1.equals(null));
        assertTrue(FR_TEST0.equals(FR_TEST0));
        assertTrue(FR_TEST1.equals(FR_TEST1));
        assertFalse(FR_TEST0.equals(new Object()));
        assertFalse(FR_TEST1.equals(FR_TEST1_NUM));

        assertTrue(FR_TEST0.equals(new VATIN(FR_TEST0_NUM)));
        assertTrue(FR_TEST1.equals(new VATIN(FR_TEST1_NUM)));
        assertFalse(FR_TEST0.equals(new VATIN(FR_TEST8_NUM)));
        assertFalse(FR_TEST1.equals(new VATIN(FR_TEST9_NUM)));
    }

    @Test(groups = "fast")
    public void shouldComputeHashCode() {
        // Expect
        assertEquals(FR_TEST0.hashCode(), 1807022424);
        assertEquals(FR_TEST1.hashCode(), 666000569);
        assertEquals(FR_TEST2.hashCode(), -475021286);
        assertEquals(FR_TEST8.hashCode(), 1637069758L);
        assertEquals(FR_TEST9.hashCode(), (17 * 37) + FR_TEST9_NUM.hashCode());
    }

    @Test(groups = "fast")
    public void shouldOutputVATIN() {
        // Expect
        assertEquals(FR_TEST0.toString(), shortIdentityToString(FR_TEST0) + "[number=" + FR_TEST0_NUM + "]");
        assertEquals(FR_TEST1.toString(), shortIdentityToString(FR_TEST1) + "[number=" + FR_TEST1_NUM + "]");
        assertEquals(FR_TEST2.toString(), shortIdentityToString(FR_TEST2) + "[number=" + FR_TEST2_NUM + "]");
        assertEquals(FR_TEST8.toString(), shortIdentityToString(FR_TEST8) + "[number=" + FR_TEST8_NUM + "]");
        assertEquals(FR_TEST9.toString(), shortIdentityToString(FR_TEST9) + "[number=" + FR_TEST9_NUM + "]");
    }
}