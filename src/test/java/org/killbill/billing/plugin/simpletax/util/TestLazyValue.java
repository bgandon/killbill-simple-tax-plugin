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

/**
 * Tests for {@link LazyValue}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestLazyValue {

    private static LazyValue<Integer, NumberFormatException> delegatingLazyValue(
            final LazyValue<Integer, NumberFormatException> delegate) {
        return new LazyValue<Integer, NumberFormatException>() {
            @Override
            protected Integer initialize() throws NumberFormatException {
                return delegate.initialize();
            }
        };
    }

    private static LazyValue<Integer, NumberFormatException> lazyValue(final String integer) {
        return new LazyValue<Integer, NumberFormatException>() {
            @Override
            protected Integer initialize() throws NumberFormatException {
                return Integer.parseInt(integer);
            }
        };
    }

    @Test(groups = "fast")
    public void shouldSetValueInitializedEvenIfNull() {
        // Given
        @SuppressWarnings("unchecked")
        LazyValue<Integer, NumberFormatException> delegateMock = mock(LazyValue.class);
        LazyValue<Integer, NumberFormatException> lazyValue = delegatingLazyValue(delegateMock);

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
        LazyValue<Integer, NumberFormatException> delegateMock = mock(LazyValue.class);
        when(delegateMock.initialize()).thenReturn(eleven);
        LazyValue<Integer, NumberFormatException> lazyValue = delegatingLazyValue(delegateMock);

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
        LazyValue<Integer, NumberFormatException> lazyValue = lazyValue("10");

        // When
        Integer integer = lazyValue.get();

        // Then
        assertEquals(integer, valueOf(10));

        // Expect
        assertSame(lazyValue.get(), integer);
    }

    @Test(groups = "fast")
    public void shouldNotMarkValueInitializedWhenThrowing() {
        // Given
        LazyValue<Integer, NumberFormatException> value = lazyValue("plop");

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
