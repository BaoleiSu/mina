/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.session;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.apache.mina.api.IdleStatus;
import org.apache.mina.api.IoFilter;
import org.apache.mina.api.IoFuture;
import org.apache.mina.api.IoService;
import org.apache.mina.api.IoSession;
import org.apache.mina.filterchain.ReadFilterChainController;
import org.apache.mina.filterchain.WriteFilterChainController;
import org.apache.mina.service.SelectorProcessor;
import org.apache.mina.service.idlechecker.IdleChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of {@link IoSession} shared with all the different transports.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractIoSession implements IoSession, ReadFilterChainController, WriteFilterChainController {
    /** The logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractIoSession.class);

    /** unique identifier generator */
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    /** The session's unique identifier */
    private final long id;

    /** The session's creation time */
    private final long creationTime;

    /** The service this session is associated with */
    private final IoService service;

    /** attributes map */
    private final AttributeContainer attributes = new DefaultAttributeContainer();

    /** The {@link SelectorProcessor} used for handling this session writing */
    protected SelectorProcessor writeProcessor;

    /** the {@link IdleChecker} in charge of detecting idle event for this session */
    protected final IdleChecker idleChecker;

    //------------------------------------------------------------------------
    // Basic statistics
    //------------------------------------------------------------------------

    /** The number of bytes read since this session has been created */
    private volatile long readBytes;

    /** The number of bytes written since this session has been created */
    private volatile long writtenBytes;

    /** Last time something was read for this session */
    private volatile long lastReadTime;

    /** Last time something was written for this session */
    private volatile long lastWriteTime;

    //------------------------------------------------------------------------
    // Session state
    //------------------------------------------------------------------------

    /** The session's state : one of CREATED, CONNECTED, CLOSING, CLOSED, SECURING, CONNECTED_SECURED */
    protected volatile SessionState state;

    /** A lock to protect the access to the session's state */
    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();

    /** A Read lock on the reentrant session's state lock */
    private final Lock stateReadLock = stateLock.readLock();

    /** A Write lock on the reentrant session's state lock */
    private final Lock stateWriteLock = stateLock.writeLock();

    /** Tells if the session is secured or not */
    protected volatile boolean secured;

    /** is this session registered for being polled for write ready events */
    private final AtomicBoolean registeredForWrite = new AtomicBoolean();

    //------------------------------------------------------------------------
    // Write queue
    //------------------------------------------------------------------------

    /** the queue of pending writes for the session, to be dequeued by the {@link SelectorProcessor} */
    private final Queue<WriteRequest> writeQueue = new DefaultWriteQueue();

    /** A lock to protect the access to the write queue */
    private final ReadWriteLock writeQueueLock = new ReentrantReadWriteLock();

    /** A Read lock on the reentrant writeQueue lock */
    private final Lock writeQueueReadLock = writeQueueLock.readLock();

    /** A Write lock on the reentrant writeQueue lock */
    private final Lock writeQueueWriteLock = writeQueueLock.writeLock();

    //------------------------------------------------------------------------
    // Filter chain
    //------------------------------------------------------------------------

    /** The list of {@link IoFilter} implementing this chain. */
    private final IoFilter[] chain;

    /** the current position in the write chain for this thread */
    private int writeChainPosition;

    /** the current position in the read chain for this thread */
    private int readChainPosition;

    /** hold the last WriteRequest created for the high level message currently written (can be null) */
    private WriteRequest lastWriteRequest;

    /**
     * Create an {@link org.apache.mina.api.IoSession} with a unique identifier (
     * {@link org.apache.mina.api.IoSession#getId()}) and an associated {@link IoService}
     * 
     * @param service the service this session is associated with
     * @param writeProcessor the processor in charge of processing this session write queue
     */
    public AbstractIoSession(IoService service, SelectorProcessor writeProcessor, IdleChecker idleChecker) {
        // generated a unique id
        id = NEXT_ID.getAndIncrement();
        creationTime = System.currentTimeMillis();
        this.service = service;
        this.writeProcessor = writeProcessor;
        this.chain = service.getFilters();
        this.idleChecker = idleChecker;

        LOG.debug("Created new session with id : {}", id);

        this.state = SessionState.CREATED;
    }

    //------------------------------------------------------------------------
    // Session State management
    //------------------------------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        try {
            stateReadLock.lock();

            return state == SessionState.CLOSED;
        } finally {
            stateReadLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosing() {
        try {
            stateReadLock.lock();

            return state == SessionState.CLOSING;
        } finally {
            stateReadLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        try {
            stateReadLock.lock();

            return state == SessionState.CONNECTED;
        } finally {
            stateReadLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCreated() {
        try {
            stateReadLock.lock();

            return state == SessionState.CREATED;
        } finally {
            stateReadLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSecuring() {
        try {
            stateReadLock.lock();

            return state == SessionState.SECURING;
        } finally {
            stateReadLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnectedSecured() {
        try {
            stateReadLock.lock();

            return state == SessionState.SECURED;
        } finally {
            stateReadLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changeState(SessionState to) throws IllegalStateException {
        try {
            stateWriteLock.lock();

            switch (state) {
            case CREATED:
                switch (to) {
                case CONNECTED:
                case SECURING:
                case CLOSING:
                    state = to;
                    break;

                default:
                    throw new IllegalStateException("Cannot transit from " + state + " to " + to);
                }

                break;

            case CONNECTED:
                switch (to) {
                case SECURING:
                case CLOSING:
                    state = to;
                    break;

                default:
                    throw new IllegalStateException("Cannot transit from " + state + " to " + to);
                }

                break;

            case SECURING:
                switch (to) {
                case SECURED:
                case CLOSING:
                    state = to;
                    break;

                default:
                    throw new IllegalStateException("Cannot transit from " + state + " to " + to);
                }

                break;

            case SECURED:
                switch (to) {
                case CONNECTED:
                case SECURING:
                case CLOSING:
                    state = to;
                    break;

                default:
                    throw new IllegalStateException("Cannot transit from " + state + " to " + to);
                }

                break;
            case CLOSING:
                if (to != SessionState.CLOSED) {
                    throw new IllegalStateException("Cannot transit from " + state + " to " + to);
                }

                state = to;

                break;

            case CLOSED:
                throw new IllegalStateException("The session is already closed. cannot switch to " + to);
            }
        } finally {
            stateWriteLock.unlock();
        }
    }

    //------------------------------------------------------------------------
    // SSL/TLS session state management
    //------------------------------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSecured() {
        return secured;
    }

    /**
     * {@inheritDoc}
     */
    public void setSecured(boolean secured) {
        this.secured = secured;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initSecure(SSLContext sslContext) throws SSLException {
        SslHelper sslHelper = new SslHelper(this, sslContext);
        sslHelper.init();

        attributes.setAttribute(SSL_HELPER, sslHelper);
        setSecured(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getReadBytes() {
        return readBytes;
    }

    /**
     * To be called by the internal plumber when some bytes are written on the socket
     * @param bytesCount number of extra bytes written
     */
    public void incrementWrittenBytes(int bytesCount) {
        writtenBytes += bytesCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getWrittenBytes() {
        return writtenBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastReadTime() {
        return lastReadTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastWriteTime() {
        return lastWriteTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final long getLastIoTime() {
        return Math.max(lastReadTime, lastWriteTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoService getService() {
        return service;
    }

    /**
     * {@inheritDoc}
     * 
     * @exception IllegalArgumentException if <code>key==null</code>
     * @see #setAttribute(AttributeKey, Object)
     */
    @Override
    public final <T> T getAttribute(AttributeKey<T> key, T defaultValue) {
        return attributes.getAttribute(key, defaultValue);
    }

    /**
     * {@inheritDoc}
     * 
     * @exception IllegalArgumentException if <code>key==null</code>
     * @see #setAttribute(AttributeKey, Object)
     */
    @Override
    public final <T> T getAttribute(AttributeKey<T> key) {
        return attributes.getAttribute(key);
    }

    /**
     * {@inheritDoc}
     * 
     * @exception IllegalArgumentException
     * <ul>
     *   <li>
     *     if <code>key==null</code>
     *   </li>
     *   <li>
     *     if <code>value</code> is not <code>null</code> and not
     *     an instance of type that is specified in by the given
     *     <code>key</code> (see {@link AttributeKey#getType()})
     *   </li>
     *  </ul>
     * 
     * @see #getAttribute(AttributeKey)
     */
    @Override
    public final <T> T setAttribute(AttributeKey<? extends T> key, T value) {
        return attributes.setAttribute(key, value);
    };

    /**
     * {@inheritDoc}
     * 
     * @see Collections#unmodifiableSet(Set)
     */
    @Override
    public Set<AttributeKey<?>> getAttributeKeys() {
        return attributes.getAttributeKeys();
    }

    /**
     * {@inheritDoc}
     * 
     * @exception IllegalArgumentException
     *                if <code>key==null</code>
     */
    @Override
    public <T> T removeAttribute(AttributeKey<T> key) {
        return attributes.removeAttribute(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(Object message) {
        doWriteWithFuture(message, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoFuture<Void> writeWithFuture(Object message) {
        IoFuture<Void> future = new DefaultWriteFuture();
        doWriteWithFuture(message, future);
        return future;
    }

    private void doWriteWithFuture(Object message, IoFuture<Void> future) {
        LOG.debug("writing message {} to session {}", message, this);

        if ((state == SessionState.CLOSED) || (state == SessionState.CLOSING)) {
            LOG.error("writing to closed or closing session, the message is discarded");
            return;
        }

        // process the queue
        processMessageWriting(message, future);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteRequest enqueueWriteRequest(Object message) {
        WriteRequest request = null;

        try {
            // Lock the queue while the message is written into it
            writeQueueReadLock.lock();

            if (isConnectedSecured()) {
                // SSL/TLS : we have to encrypt the message
                SslHelper sslHelper = getAttribute(SSL_HELPER, null);

                if (sslHelper == null) {
                    throw new IllegalStateException();
                }

                request = sslHelper.processWrite(this, message, writeQueue);
            } else {
                // Plain message
                request = new DefaultWriteRequest(message);

                writeQueue.add(request);
            }
        } finally {
            writeQueueReadLock.unlock();
        }

        // If it wasn't, we register this session as interested to write.
        // It's done in atomic fashion for avoiding two concurrent registering.
        if (!registeredForWrite.getAndSet(true)) {
            writeProcessor.flush(this);
        }

        return request;
    }

    public void setNotRegisteredForWrite() {
        registeredForWrite.set(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Queue<WriteRequest> acquireWriteQueue() {
        writeQueueWriteLock.lock();
        return writeQueue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void releaseWriteQueue() {
        writeQueueWriteLock.unlock();
    }

    //------------------------------------------------------------------------
    // Event processing using the filter chain
    //------------------------------------------------------------------------

    /**
     * process session create event using the filter chain. To be called by the session {@link SelectorProcessor} .
     */
    public void processSessionCreated() {
        LOG.debug("processing session created event for session {}", this);

        for (IoFilter filter : chain) {
            filter.sessionCreated(this);
        }
    }

    /**
     * process session opened event using the filter chain. To be called by the session {@link SelectorProcessor} .
     */
    public void processSessionOpened() {
        LOG.debug("processing session open event");

        for (IoFilter filter : chain) {
            filter.sessionOpened(this);
        }
    }

    /**
     * process session closed event using the filter chain. To be called by the session {@link SelectorProcessor} .
     */
    public void processSessionClosed() {
        LOG.debug("processing session closed event");

        for (IoFilter filter : chain) {
            filter.sessionClosed(this);
        }
    }

    /**
     * process session idle event using the filter chain. To be called by the session {@link SelectorProcessor} .
     */
    public void processSessionIdle(IdleStatus status) {
        LOG.debug("processing session idle {} event for session {}", status, this);

        for (IoFilter filter : chain) {
            filter.sessionIdle(this, status);
        }
    }

    /**
     * process session message received event using the filter chain. To be called by the session {@link SelectorProcessor} .
     * @param message the received message 
     */
    public void processMessageReceived(ByteBuffer message) {
        LOG.debug("processing message '{}' received event for session {}", message, this);

        // save basic statistics 
        readBytes += message.remaining();
        lastReadTime = System.currentTimeMillis();

        if (chain.length < 1) {
            LOG.debug("Nothing to do, the chain is empty");
        } else {
            readChainPosition = 0;
            // we call the first filter, it's supposed to call the next ones using the filter chain controller
            chain[readChainPosition].messageReceived(this, message, this);
        }
    }

    /**
     * process session message writing event using the filter chain. To be called by the session {@link SelectorProcessor} .
     * @param message the wrote message, should be transformed into ByteBuffer at the end of the filter chain 
     */
    public void processMessageWriting(Object message, IoFuture<Void> future) {
        LOG.debug("processing message '{}' writing event for session {}", message, this);

        lastWriteRequest = null;

        if (chain.length < 1) {
            enqueueFinalWriteMessage(message);
        } else {
            writeChainPosition = chain.length - 1;
            // we call the first filter, it's supposed to call the next ones using the filter chain controller
            int position = writeChainPosition;
            IoFilter nextFilter = chain[position];
            nextFilter.messageWriting(this, message, this);
        }

        // put the future in the last write request
        if (future != null) {
            WriteRequest request = lastWriteRequest;

            if (request != null) {
                ((DefaultWriteRequest) request).setFuture(future);
            }
        }
    }

    /**
     * process session message received event using the filter chain. To be called by the session {@link SelectorProcessor} .
     * @param message the received message 
     */
    @Override
    public void callWriteNextFilter(Object message) {
        LOG.debug("calling next filter for writing for message '{}' position : {}", message, writeChainPosition);

        writeChainPosition--;

        if (writeChainPosition < 0 || chain.length == 0) {
            // end of chain processing
            enqueueFinalWriteMessage(message);
        } else {
            chain[writeChainPosition].messageWriting(this, message, this);
        }

        writeChainPosition++;
        ;
    }

    /**
     * At the end of write chain processing, enqueue final encoded {@link ByteBuffer} message in the session
     */
    private void enqueueFinalWriteMessage(Object message) {
        LOG.debug("end of write chan we enqueue the message in the session : {}", message);
        lastWriteRequest = enqueueWriteRequest(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void callReadNextFilter(Object message) {
        readChainPosition++;

        if (readChainPosition >= chain.length) {
            // end of chain processing
        } else {
            chain[readChainPosition].messageReceived(this, message, this);
        }

        readChainPosition--;
    }

}