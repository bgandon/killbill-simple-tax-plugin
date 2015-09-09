package org.killbill.billing.test.helpers;

/**
 * Marker interface for builders that can build other objects.
 *
 * @author Benjamin Gandon
 * @param <T>
 *            the type of objects that this builder can build
 */
public interface Builder<T> {
    public T build();
}
