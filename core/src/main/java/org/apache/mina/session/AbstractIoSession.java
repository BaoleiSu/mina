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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.mina.IoService;
import org.apache.mina.IoSession;
import org.apache.mina.WriteFuture;
import org.apache.mina.service.SelectorProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of {@link IoSession} shared with all the different
 * transports.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractIoSession implements IoSession {
    /** The logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractIoSession.class);

    /** The session's unique identifier */
    private final long id;

    /** The session's creation time */
    private final long creationTime;

    /** The service this session is associated with */
    private final IoService service;

    /** The number of bytes read since this session has been created */
    private volatile long readBytes;

    /** The number of bytes written since this session has been created */
    private volatile long writtenBytes;

    /** Last time something was read for this session */
    private volatile long lastReadTime;

    /** Last time something was written for this session */
    private volatile long lastWriteTime;

    /** attributes map */
    private final Map<Object, Object> attributes = new ConcurrentHashMap<Object, Object>(4);

    /** unique identifier generator */
    private static final AtomicLong NEXT_ID = new AtomicLong(0);
    
    protected Object stateMonitor = new Object();
    protected SessionState state;
    
    /** the queue of pending writes for the session, to be dequeued by the {@link SelectorProcessor} */
    private WriteQueue writeQueue = new DefaultWriteQueue();
    
    /**
     * Create an {@link IoSession} with a unique identifier (
     * {@link IoSession#getId()}) and an associated {@link IoService}
     * 
     * @param service the service this session is associated with
     */
    public AbstractIoSession(IoService service) {
        // generated a unique id
        id = NEXT_ID.getAndIncrement();
        creationTime = System.currentTimeMillis();
        this.service = service;
        LOG.debug("Created new session with id : {}", id);
        synchronized (stateMonitor) {
            this.state=SessionState.CREATED;
		}
    }

    public SessionState getState() {
        return state;
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
     */
    @Override
    public Object getAttribute(Object name) {
        return attributes.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object setAttribute(Object name, Object value) {
        return attributes.put(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsAttribute(Object name) {
        return attributes.containsKey(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object removeAttribute(Object name) {
        return attributes.remove(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Object> getAttributeNames() {
        return attributes.keySet();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void write(Object message) {
    	if (state == SessionState.CLOSED || state == SessionState.CLOSING) {
    		// TODO actually we just just shallow the message if the session is closed/closing
    		LOG.error("writing to closed or cloing session");
    		return;
    	}
    	writeQueue.add(new DefaultWriteRequest(message));
    	
    	// mark as interested to write
    }
    
    @Override
    public WriteFuture writeWithFuture(Object message) {
    	write(message);
    	// TODO implements WriteFuture
    	return null;
    }
}