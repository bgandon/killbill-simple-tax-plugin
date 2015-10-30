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
package org.killbill.billing.plugin.simpletax.util;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * A {@link ToStringStyle} that prints out the short class name without
 * sacrifying the identity hashcode like
 * {@link org.apache.commons.lang3.builder.ToStringStyle#SHORT_PREFIX_STYLE}
 * does.
 *
 * @author Benjamin Gandon
 */
public final class ShortToStringStyle extends ToStringStyle {
    private static final long serialVersionUID = 1L;

    /**
     * The short toString style. Using the {@code Person} example from
     * {@link ToStringBuilder}, the output would look like this:
     *
     * <pre>
     * Person@182f0db[name=John Doe,age=33,smoker=false]
     * </pre>
     */
    public static final ToStringStyle SHORT_STYLE = new ShortToStringStyle();

    /**
     * Private constructor, because of the singleton nature of this class. Use
     * the {@link #SHORT_STYLE} to get the singleton instance.
     */
    private ShortToStringStyle() {
        super();
        setUseShortClassName(true);
    }

    /**
     * Ensure the consistency of <em>singleton</em> pattern after
     * deserialization.
     *
     * @return the singleton
     */
    private Object readResolve() {
        return SHORT_STYLE;
    }
}
