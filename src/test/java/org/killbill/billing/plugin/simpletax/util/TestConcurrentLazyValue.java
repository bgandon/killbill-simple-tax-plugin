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

import static com.googlecode.catchexception.CatchException.catchException;
import static com.googlecode.catchexception.CatchException.caughtException;
import static com.googlecode.catchexception.CatchException.resetCaughtException;
import static java.lang.Integer.parseInt;
import static java.lang.Integer.valueOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import org.testng.annotations.Test;

import com.google.common.base.Supplier;

/**
 * Tests for {@link ConcurrentLazyValue}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestConcurrentLazyValue {

    private static class DelegatingLazyValue extends ConcurrentLazyValue<Integer> {
        private final ConcurrentLazyValue<Integer> delegate;

        public DelegatingLazyValue(ConcurrentLazyValue<Integer> delegate) {
            this.delegate = delegate;
        }

        @Override
        protected Integer initialize() throws NumberFormatException {
            return delegate.initialize();
        }
    }

    private static class ParseInt extends ConcurrentLazyValue<Integer> {
        private final String integer;

        public ParseInt(String integer) {
            this.integer = integer;
        }

        @Override
        protected Integer initialize() throws NumberFormatException {
            return parseInt(integer);
        }
    }

    @Test(groups = "fast")
    public void shouldSetValueInitializedEvenIfNull() {
        // Given
        @SuppressWarnings("unchecked")
        ConcurrentLazyValue<Integer> delegateMock = mock(ConcurrentLazyValue.class);
        Supplier<Integer> lazyValue = new DelegatingLazyValue(delegateMock);

        // When
        Object value = lazyValue.get();
        lazyValue.get();

        // Then
        assertNull(value);
        verify(delegateMock).initialize();
    }

    @Test(groups = "fast")
    public void shouldCallInitializedOnlyOnce() {
        // Given
        Integer eleven = new Integer(11);
        @SuppressWarnings("unchecked")
        ConcurrentLazyValue<Integer> delegateMock = mock(ConcurrentLazyValue.class);
        when(delegateMock.initialize()).thenReturn(eleven);
        Supplier<Integer> lazyValue = new DelegatingLazyValue(delegateMock);

        // When
        Object value = lazyValue.get();
        lazyValue.get();

        // Then
        assertEquals(value, eleven);
        assertSame(value, eleven);
        verify(delegateMock).initialize();
    }

    @Test(groups = "fast")
    public void shouldGetSameValue() {
        // Given
        Supplier<Integer> lazyValue = new ParseInt("10");

        // When
        Integer integer = lazyValue.get();

        // Then
        assertEquals(integer, valueOf(10));

        // Expect
        assertSame(lazyValue.get(), integer);
    }

    @Test(groups = "fast")
    public void shouldNotMarkValueInitializedWhenThrowing() throws Exception {
        // Given
        ConcurrentLazyValue<Integer> value = new ParseInt("plop");

        // When
        catchException(value).get();

        // Then
        Exception exc1 = caughtException();
        assertNotNull(exc1);
        assertEquals(exc1.getClass(), NumberFormatException.class);

        // Given
        resetCaughtException();

        // When
        catchException(value).get();

        // Then
        Exception exc2 = caughtException();
        assertNotNull(exc2);
        assertNotSame(exc2, exc1);
        assertEquals(exc2.getClass(), NumberFormatException.class);
    }
}
