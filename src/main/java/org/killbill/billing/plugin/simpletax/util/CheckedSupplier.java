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

/**
 * A class that can supply objects of a single type, and throw a checked
 * exception when supplying a value. No guarantees are implied by this
 * interface.
 * <p>
 * This is inspired by {@link com.google.common.base.Supplier Supplier} that
 * made it as standard {@code java.util.Supplier} in Java 8. The only difference
 * here is that supplying a value is not limited to throwing
 * {@linkplain java.lang.RuntimeException unchecked exceptions}.
 *
 * @param <T>
 *            The type of instances that are supplied.
 * @param <E>
 *            The type of (checked) exception that might happen when supplying
 *            an instance.
 * @author Benjamin Gandon
 * @see com.google.common.base.Supplier
 */
public interface CheckedSupplier<T, E extends Exception> {

    /**
     * Retrieves an instance of the appropriate type. The returned object may or
     * may not be a new instance, depending on the implementation.
     *
     * @return An instance of the appropriate type.
     * @throws E
     *             If an error occurred when retrieving the requested instance.
     */
    public abstract T get() throws E;
}