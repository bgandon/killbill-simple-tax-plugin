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

import static com.google.common.base.Preconditions.checkState;

/**
 * A simple promise object.
 *
 * @author Benjamin Gandon
 * @param <T>
 *            The type of the object that is promised.
 */
public class Promise<T> {

    private boolean isSet = false;
    private T value;

    /**
     * Shortcut factory method.
     *
     * @return A new promise instance.
     */
    public static <T> Promise<T> holder() {
        return new Promise<T>();
    }

    private Promise() {
        super();
    }

    /**
     * Resolve the promise with the given instance.
     *
     * @param value
     *            The value for the promised object.
     * @throws IllegalStateException
     *             When the promise has already been resolved.
     */
    public synchronized void resolve(T value) {
        checkState(!isSet, "already resolved promise");
        this.value = value;
        isSet = true;
    }

    /**
     * Obtain the promised instance.
     *
     * @return The promised object.
     * @throws IllegalStateException
     *             When the promise has not already been resolved.
     */
    public synchronized T get() {
        checkState(isSet, "unresolved promise");
        return value;
    }
}