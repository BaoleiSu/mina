/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.mina.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.mina.api.IoFuture;
import org.apache.mina.api.IoFutureListener;

/**
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultIoFuture<V> implements IoFuture<V> {
    static final Logger LOG = LoggerFactory.getLogger(DefaultIoFuture.class);
    private final CountDownLatch latch = new CountDownLatch(1);
    private final List<IoFutureListener<V>> listeners = new ArrayList<IoFutureListener<V>>();
    private final AtomicReference<Object> result = new AtomicReference<Object>();
    private final FutureResultOwner owner;
    private volatile boolean canceled;

    /**
     * There may be many futures but there will be a single result owner.  The
     * interface {@link FutureResultOwner} allows instances of {@link DefaultIoFuture}
     * to call cancel on the actual owner of the future result.
     *
     * @param owner the owner of the future result
     */
    DefaultIoFuture(FutureResultOwner owner) {
        this.owner = owner;
    }

    /**
     * Set the future result as a {@link Throwable}, indicating that a
     * throwable was thrown while executing the task.  This value is usually
     * set by the future result owner.
     * <p/>
     * Any {@link IoFutureListener}s are notified of the exception.
     *
     * @param t the throwable that was thrown while executing the task.
     */
    public void exception(Throwable t) {
        assert !isDone();

        synchronized (latch) {
            result.set(t);
            latch.countDown();

            for (IoFutureListener<V> listener : listeners) {
                listener.exception(t);
            }
        }

        listeners.clear();
    }

    /**
     * Set the future result of the executing task.  Any {@link IoFutureListener}s
     * are notified of the
     *
     * @param value the value returned by the executing task.
     */
    public void set(V value) {
        assert !isDone();

        synchronized (latch) {
            result.set(value);
            latch.countDown();
        }

        for (IoFutureListener<V> listener : listeners) {
            listener.completed(value);
        }

        listeners.clear();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    public IoFuture<V> register(IoFutureListener<V> listener) {
        synchronized (latch) {
            if (!isDone()) {
                listeners.add(listener);
                listener = null;
            }
        }

        if (listener != null) {
            Object object = result.get();
            if (object instanceof Throwable) {
                listener.exception(new ExecutionException((Throwable) object));
            } else {
                listener.completed((V) object);
            }
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (latch) {
            boolean c = !canceled && !isDone() && owner.cancel(mayInterruptIfRunning);

            if (c) canceled = true;

            return canceled;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isCancelled() {
        return canceled;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDone() {
        return latch.getCount() == 0;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    public V get() throws InterruptedException, ExecutionException {
        latch.await();

        Object object = result.get();
        if (object instanceof Throwable) {
            throw new ExecutionException((Throwable) object);
        } else {
            return (V) object;
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!latch.await(timeout, unit)) throw new TimeoutException();

        Object object = result.get();
        if (object instanceof Throwable) {
            throw new ExecutionException((Throwable) object);
        } else {
            return (V) object;
        }
    }

    public static interface FutureResultOwner {
        boolean cancel(boolean mayInterruptIfRunning);
    }
}
