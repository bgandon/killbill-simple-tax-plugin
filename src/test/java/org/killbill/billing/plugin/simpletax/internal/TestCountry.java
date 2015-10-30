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

import static java.util.Locale.ENGLISH;
import static java.util.Locale.FRENCH;
import static org.killbill.billing.test.helpers.TestUtil.shortIdentityToString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestCountry {

    private static final Object[][] LEGAL_COUNTRY_CODES = { { "FR" }, { "BE" }, { "CA" }, { "CH" }, { "MA" }, { "DZ" },
            { "TN" }, { "CD" }, { "MU" } };
    private static final Object[][] ILLEGAL_COUNTRY_CODES = { { "" }, { " " }, { "\t" }, { "toto" }, { ".." },
            { "??" }, { "**" }, { " FR" }, { "FR\t" }, { "FRA" } };

    private static final Country US = new Country("US");
    private static final Country FR = new Country("FR");

    @DataProvider(name = "legalCountryCodes")
    public static Object[][] legalCountryCodes() {
        return LEGAL_COUNTRY_CODES;
    }

    @DataProvider(name = "illegalCountryCodes")
    public static Object[][] illegalCountryCodes() {
        return ILLEGAL_COUNTRY_CODES;
    }

    @Test(groups = "fast", dataProvider = "illegalCountryCodes", expectedExceptions = IllegalArgumentException.class)
    public void shouldRejectIllegalCountryCodes(String illegalCountryCode) {
        // Expect exception
        new Country(illegalCountryCode);
    }

    @Test(groups = "fast", dataProvider = "legalCountryCodes")
    public void shouldReturnCountryCode(String legalCountryCode) {
        // Expect
        assertEquals(new Country(legalCountryCode).getCode(), legalCountryCode);
    }

    @Test(groups = "fast")
    public void shouldReturnLocalizedName() {
        // Expect
        assertEquals(US.computeName(FRENCH), "Etats-Unis");
        assertEquals(FR.computeName(ENGLISH), "France");
    }

    @Test(groups = "fast")
    public void shouldEnforceEquality() {
        // Expect
        assertFalse(FR.equals(null));
        assertTrue(FR.equals(FR));
        assertFalse(FR.equals(new Object()));
        assertTrue(FR.equals(FR));
        assertFalse(FR.equals(US));
    }

    @Test(groups = "fast")
    public void shouldComputeHashCode() {
        // Expect
        assertEquals(FR.hashCode(), 2881);
        assertEquals(US.hashCode(), 3347);
    }

    @Test(groups = "fast")
    public void shouldOutputCountryCode() {
        // Expect
        assertEquals(FR.toString(), shortIdentityToString(FR) + "[code=FR]");
        assertEquals(US.toString(), shortIdentityToString(US) + "[code=US]");
    }
}
