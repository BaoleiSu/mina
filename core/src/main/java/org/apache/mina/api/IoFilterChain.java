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

package org.apache.mina.api;

import java.util.List;

/**
 * An implementation that is responsible for performing IO (network, file or
 * any other kind of IO)
 *
 * The chain will look something like
 *
 *         Upstream Chain                 DownStream
 *
 *        IoHandler Filter                IoHandler Filter
 *              /|\                              |
 *               |                              \|/
 *           Filter N                        Filter D
 *              /|\                              |
 *               |                              \|/
 *           Filter C                        Filter E
 *              /|\                              |
 *               |                              \|/
 *           Filter B                        Filter F
 *              /|\                              |
 *               |                              \|/
 *           Filter A                      Acceptor/Socket
 *              /|\
 *               |
 *         Acceptor/Socket
 *
 *
 *
 * TODO
 * 1. How to handle the insertion in between the Filter's. Do we need an API?
 * 2. What to do with the fireEvent* methods?
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoFilterChain {

    /**
     * Returns all the filters that are currently present in the chain.
     * Useful to the know the current processing chain. The chain is returned
     * in the order of processing (the first filter in the list shall be the
     * first one to be processed)
     *
     * @return  List of all {@link IoFilter} present in the chain
     */
    List<IoFilter> getChain();

    /**
     * Call this method for processing a session created event using this chain.
     * @param session {@link IoSession} the freshly created session
     */
    void processSessionCreated(IoSession session);

    /**
     * Call this method for processing a session open event using this chain.
     * @param session {@link IoSession} the opened session
     */
    void processSessionOpen(IoSession session);

    /**
     * Call this method for processing a received message using this chain.
     * This processing is done in reverse order.
     * @param session {@link IoSession} associated with this message
     * @param message the received message
     * @return the message after the processing of each filter
     */
    Object processMessageReceived(IoSession session, Object message);

    /**
     * Call this method for processing a message for writing using this chain.
     * @param session {@link IoSession} associated with this message
     * @param message the message to write
     * @return the message after the processing
     */
    Object processMessageWriting(IoSession session, Object message);
}