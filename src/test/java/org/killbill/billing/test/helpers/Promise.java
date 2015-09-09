package org.killbill.billing.test.helpers;

import static com.google.common.base.Preconditions.checkState;

public class Promise<T> {
    boolean isSet = false;
    T value;

    public synchronized void resolve(final T value) {
        checkState(!isSet, "already resolved promise");
        this.value = value;
        isSet = true;
    }

    public synchronized T get() {
        checkState(isSet, "unresolved promise");
        return value;
    }
}