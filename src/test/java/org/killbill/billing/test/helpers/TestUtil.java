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

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;

/**
 * Common utility methods used in tests.
 *
 * @author Benjamin Gandon
 */
public final class TestUtil {
    private TestUtil() {
    }

    /**
     * An {@link org.apache.commons.lang3.ObjectUtils#identityToString(Object)
     * identityToString()} variant with short class name.
     * 
     * @param obj
     *            The object to create a toString for, may be {@code null}.
     * @return The default toString text, or {@code null} if {@code null} passed
     *         in.
     * @see org.apache.commons.lang3.ObjectUtils#identityToString(Object)
     */
    public static String shortIdentityToString(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.getClass().getSimpleName() + '@' + toHexString(identityHashCode(obj));
    }
}
