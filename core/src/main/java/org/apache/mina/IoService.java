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
package org.apache.mina;

import java.util.Map;

/**
 * Base interface for all {@link IoAcceptor}s and {@link IoConnector}s
 * that provide I/O service and manage {@link IoSession}s.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoService {
    
    /**
     * Returns the map of all sessions which are currently managed by this
     * service.  The key of map is the {@link IoSession#getId() ID} of the
     * session.
     *
     * @return the sessions. An empty collection if there's no session.
     */
    Map<Long, IoSession> getManagedSessions();
    
    /**
     * Adds an {@link IoServiceListener} that listens any events related with
     * this service.
     */
    void addListener(IoServiceListener listener);

    /**
     * Removed an existing {@link IoServiceListener} that listens any events
     * related with this service.
     */
    void removeListener(IoServiceListener listener);
}
