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
package org.apache.mina.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.IoService;
import org.apache.mina.IoServiceListener;
import org.apache.mina.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for {@link IoService}s.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractIoService implements IoService {

    static final Logger LOG = LoggerFactory.getLogger(AbstractIoService.class);
    
    private final Map<Long, IoSession> managedSessions = new ConcurrentHashMap<Long, IoSession>();
    
    /**
     * The handler, the interface with the application part.
     */
    private IoHandler handler;

    /**
     * Placeholder for storing all the listeners added
     */
    private final List<IoServiceListener> listeners = new CopyOnWriteArrayList<IoServiceListener>(); 

    @Override
    public Map<Long, IoSession> getManagedSessions() {
        return managedSessions;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void addListener(IoServiceListener listener) {
        if(listener != null) {
            listeners.add(listener);
            return;
        }

        LOG.warn("Trying to add Null Listener");
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void removeListener(IoServiceListener listener) {
        if(listener != null) {
            listeners.remove(listener);    
        }
    }


    /**
     * {@inheritDoc}
     */
    public final IoHandler getHandler() {
        return handler;
    }


    /**
     * {@inheritDoc}
     */
    public final void setHandler(IoHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null");
        }

        // TODO: check the service state, we should not be able to set the handler
        // if the service is already started
        /*
        if (isActive()) {
            throw new IllegalStateException(
                    "handler cannot be set while the service is active.");
        }
        */

        this.handler = handler;
    }
}