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

import com.google.common.base.Supplier;

/**
 * A {@link Supplier} and holder for a value that can be lazily initialized with
 * thread-safety guarantees.
 * <p>
 * Useful to optionally initialize a complex value only once in a concurrent
 * context, without caring for the details of any unchecked exception that could
 * be thrown at that moment.
 *
 * @param <T>
 *            The type of the lazy value.
 * @author Benjamin Gandon
 */
public abstract class ConcurrentLazyValue<T> extends LazyValue<T> {

    /** Whether the value is initialized. */
    private boolean initialized = false;

    /** Stores the managed object. */
    private T value;

    /**
     * Returns the value wrapped by this instance, initializing it on first
     * access. Subsequent access return the cached value.
     *
     * @return The object initialized by this {@code LazyValue}.
     */
    @Override
    public T get() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    value = initialize();
                    initialized = true;
                }
            }
        }
        return value;
    }

}
