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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

/**
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestVATINValidator {

    private VATINValidator validator = new VATINValidator();

    @Test(groups = "fast")
    public void shouldValidate() {
        // Expect
        assertTrue(validator.apply("ATU12345678"));
        assertFalse(validator.apply("AT 12345678"));

        assertTrue(validator.apply("BE0123456789"));
        assertTrue(validator.apply("BE123456789"));
        assertFalse(validator.apply("BE 123456789"));

        assertTrue(validator.apply("BG123456789"));
        assertTrue(validator.apply("BG1234567890"));
        assertFalse(validator.apply("BG1234567890\n"));

        assertTrue(validator.apply("CHE123456789"));
        assertTrue(validator.apply("CHE123456789MWST"));
        assertFalse(validator.apply("CHE123456789 "));

        assertTrue(validator.apply("CY01234567A"));
        assertTrue(validator.apply("CY51234567Z"));
        assertTrue(validator.apply("CY91234567Z"));
        assertFalse(validator.apply("CY61234567Z"));

        assertTrue(validator.apply("CZ12345678"));
        assertTrue(validator.apply("CZ123456789"));
        assertTrue(validator.apply("CZ1234567890"));
        assertTrue(validator.apply("CZ12345678123"));
        assertTrue(validator.apply("CZ123456789123"));
        assertTrue(validator.apply("CZ1234567890123"));
        assertFalse(validator.apply("CZ1234567"));
        assertFalse(validator.apply("CZ12345678901234"));

        assertTrue(validator.apply("DE912345678"));
        assertFalse(validator.apply("DE012345678"));

        assertTrue(validator.apply("DK12345678"));
        assertFalse(validator.apply("DK1234567"));
        assertFalse(validator.apply("DK123456789"));

        assertTrue(validator.apply("EE101234567"));
        assertFalse(validator.apply("EE1012345678"));
        assertFalse(validator.apply("EE011234567"));

        assertTrue(validator.apply("EL123456789"));
        assertFalse(validator.apply("EL12345678"));
        assertFalse(validator.apply("EL1234567890"));

        assertFalse(validator.apply("ZZ123456789"));
    }
}
