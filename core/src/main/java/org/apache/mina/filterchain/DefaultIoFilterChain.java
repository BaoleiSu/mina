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

import org.apache.mina.api.IoFilter;
import org.apache.mina.api.IoFilterChain;
import org.apache.mina.api.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultIoFilterChain implements IoFilterChain, ReadFilterChainController, WriteFilterChainController {

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
    public void processSessionCreated(IoSession session) {
        LOG.debug("processing session created event");
        for (IoFilter filter : chain) {
            filter.sessionCreated(session);
        }
    }

    @Override
    public void processSessionOpen(IoSession session) {
        LOG.debug("processing session open event");
        for (IoFilter filter : chain) {
            filter.sessionOpened(session);
        }
    }

    @Override
    public void processSessionClosed(IoSession session) {
        LOG.debug("processing session closed event");
        for (IoFilter filter : chain) {
            filter.sessionClosed(session);
        }
    }

    @Override
    public void processMessageReceived(IoSession session, Object message) {
        LOG.debug("processing message '{}' received event ", message);
        if (chain.isEmpty()) {
            LOG.debug("Nothing to do, the chain is empty");
        } else {
            // we call the first filter, it's supposed to call the next ones using the filter chain controller
            chain.get(0).messageReceived(session, message, this, 0);
        }
    }

    @Override
    public void processMessageWriting(IoSession session, Object message) {
        LOG.debug("processing message '{}' writing event ", message);
        if (chain.isEmpty()) {
            LOG.debug("Nothing to do, the chain is empty");
        } else {
            // we call the first filter, it's supposed to call the next ones using the filter chain controller
            chain.get(0).messageWriting(session, message, this, 0);
        }
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

    @Override
    public void callWriteNextFilter(IoSession session, int currentPosition, Object message) {
        currentPosition--;
        if (currentPosition < 0 || chain.size() == 0) {
            // end of chain processing
            session.enqueueWriteRequest(message);
        } else {
            chain.get(currentPosition).messageWriting(session, message, this, currentPosition);
        }
    }

    @Override
    public void callReadNextFilter(IoSession session, int currentPosition, Object message) {
        currentPosition++;
        if (currentPosition >= chain.size()) {
            // end of chain processing
        } else {
            chain.get(currentPosition).messageReceived(session, message, this, currentPosition);
        }
    }

}