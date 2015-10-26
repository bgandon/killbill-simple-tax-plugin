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
package org.killbill.billing.test.helpers;

import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;

/**
 * Assertion helpers that don't exist in {@link org.testng.Assert}.
 *
 * @author Benjamin Gandon
 */
public abstract class Assert {

    /**
     * Helper assert that ignores {@linkplain BigDecimal#scale() scales}.
     *
     * @param actual
     *            the actual value
     * @param expected
     *            the expected value
     */
    public static void assertEqualsIgnoreScale(BigDecimal actual, BigDecimal expected) {
        assertTrue(expected.compareTo(actual) == 0);
    }

}
