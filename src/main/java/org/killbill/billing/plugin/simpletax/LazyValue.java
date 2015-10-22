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
package org.killbill.billing.plugin.simpletax;

/**
 * A simple holder for a lazily initialized value.
 * <p>
 * Useful to optionally initialize a complex value only once.
 * <p>
 * Highly inspired by
 * {@link org.apache.commons.lang3.concurrent.LazyInitializer}, but no thread
 * safety is implemented here.
 *
 * @author Benjamin Gandon
 * @param <T>
 *            The type of the lazy value
 * @param <E>
 *            The type of error that might happen when initializing the value.
 */
public abstract class LazyValue<T, E extends Exception> {

    /** Whether the value is initialized. */
    private boolean initialized = false;

    /** Stores the managed object. */
    private T value;

    /**
     * Returns the value wrapped by this instance, initializing it on first
     * access. Subsequent access return the cached value.
     *
     * @return the object initialized by this {@code LazyInitializer}
     * @throws E
     *             if an error occurred during initialization of the object
     */
    public T get() throws E {
        if (!initialized) {
            value = initialize();
            initialized = true;
        }
        return value;
    }

    /**
     * Initializes the value managed by this instance. This method is called by
     * {@link #get()} when the object is accessed for the first time.
     * <p>
     * This method is guaranteed to be called only once, even if the initialized
     * value is {@code null}.
     *
     * @return the managed data object
     * @throws E
     *             if an error occurs during object creation
     */
    protected abstract T initialize() throws E;
}
