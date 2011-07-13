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
package org.apache.mina.filterchain;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.mina.api.IoFilter;
import org.apache.mina.api.IoFilterChain;
import org.apache.mina.api.IoSession;

public class DefaultIoFilterChain implements IoFilterChain {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultIoFilterChain.class);

    /**
     * The list of {@link IoFilter} implementing this chain.
     */
    private final List<IoFilter> chain;

    /**
     * The instance of {@link DefaultIoFilterChain} with an empty chain.
     */
    public DefaultIoFilterChain() {
        this.chain = new ArrayList<IoFilter>();
    }

    /**
     * The instance of {@link DefaultIoFilterChain} is initialized with a copy
     * of a filter chain.
     *
     * @param chain the chain to be copied
     */
    public DefaultIoFilterChain(List<IoFilter> chain) {
        this.chain = new ArrayList<IoFilter>(chain);
    }

    @Override
    public List<IoFilter> getChain() {
        return chain;
    }

    @Override
    public void processExceptionCaught(IoSession session, Throwable cause) {
        for (IoFilter filter : chain) {

            try {
                filter.exceptionCaught(session, cause);
            } catch (Exception e) {
                LOG.error("Exception caught during processing exception caught event", e);
            }
        }
    }

    @Override
    public void processSessionCreated(IoSession session) {
        for (IoFilter filter : chain) {
            try {
                filter.sessionCreated(session);
            } catch (Exception e) {
                LOG.error("Exception caught during processing session created event", e);
                // we re-forward the catched Exception
                processExceptionCaught(session, e);
            }
        }
    }

    @Override
    public void processSessionOpen(IoSession session) {
        for (IoFilter filter : chain) {
            try {
                filter.sessionOpened(session);
            } catch (Exception e) {
                LOG.error("Exception caught during processing session open event", e);
                // we re-forward the catched Exception
                processExceptionCaught(session, e);
            }
        }
    }

    @Override
    public Object processMessageReceived(IoSession session, Object message) {
        for (IoFilter filter : chain) {
            try {
                message = filter.messageReceived(session, message);
            } catch (Exception e) {
                LOG.error("Exception caught during processing message received event", e);
                // we re-forward the catched Exception
                processExceptionCaught(session, e);
            }
        }
        return message;
    }

    @Override
    public Object processMessageWriting(IoSession session, Object message) {
        int len = chain.size();
        for (int i = 1; i <= len; i++) {
            try {
                message = chain.get(len - i).messageWriting(session, message);
            } catch (Exception e) {
                LOG.error("Exception caught during processing message writing event", e);
                // we re-forward the catched Exception
                processExceptionCaught(session, e);
            }
        }
        return message;
    }

    @Override
    public String toString() {
        StringBuilder bldr = new StringBuilder("IoFilterChain {");
        int index = 0;
        for (IoFilter filter : chain) {
            bldr.append(index).append(":").append(filter).append(", ");
        }
        return bldr.append("}").toString();
    }

}