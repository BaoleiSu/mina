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

/**
 * Filter are interceptors/processors for incoming data received/sent.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoFilter {

    // ---- Events Functions ---
    /**
     * Invoked from an I/O processor thread when a new connection has been created. Because this method is supposed to
     * be called from the same thread that handles I/O of multiple sessions, please implement this method to perform
     * tasks that consumes minimal amount of time such as socket parameter and user-defined session attribute
     * initialization.
     * 
     * @param session {@link IoSession} associated with the invocation
     * 
     * @throws Exception Exception If an error occurs while processing
     */
    void sessionCreated(IoSession session) throws Exception;

    /**
     * Invoked when a connection has been opened.
     * 
     * @param session {@link IoSession} associated with the invocation
     * @throws Exception Exception If an error occurs while processing
     */
    void sessionOpened(IoSession session) throws Exception;

    /**
     * Invoked when a connection is closed.
     * 
     * @param session {@link IoSession} associated with the invocation
     * @throws Exception Exception If an error occurs while processing
     */
    void sessionClosed(IoSession session) throws Exception;

    /**
     * Invoked with the related {@link IdleStatus} when a connection becomes idle.
     * 
     * @param session {@link IoSession} associated with the invocation
     * @throws Exception Exception If an error occurs while processing
     */
    void sessionIdle(IoSession session, IdleStatus status) throws Exception;

    /**
     * Invoked when a message is received.
     * 
     * @param session {@link IoSession} associated with the invocation
     * @param message the incoming message to process
     * @return the message after processing
     * @throws Exception Exception If an error occurs while processing
     */
    Object messageReceived(IoSession session, Object message) throws Exception;

    /**
     * Invoked when a message is under writing. The filter is supposed to apply the needed transformation.
     * 
     * @param session {@link IoSession} associated with the invocation
     * @param message the message to process before writing
     * @throws Exception Exception If an error occurs while processing
     * @return the message after processing
     */
    Object messageWriting(IoSession session, Object message) throws Exception;

    /**
     * Invoked when an exception occurs while executing the method
     * 
     * @param session {@link IoSession} associated with invocation
     * @param cause Real {@link Throwable} which broke the normal chain processing
     * @throws Exception If an error occurs while processing
     */
    void exceptionCaught(IoSession session, Throwable cause) throws Exception;
}