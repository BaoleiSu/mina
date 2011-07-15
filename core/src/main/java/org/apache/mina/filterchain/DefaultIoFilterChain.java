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
    public void processSessionCreated(IoSession session) {
        for (IoFilter filter : chain) {
            filter.sessionCreated(session);
        }
    }

    @Override
    public void processSessionOpen(IoSession session) {
        for (IoFilter filter : chain) {
            filter.sessionOpened(session);
        }
    }

    @Override
    public Object processMessageReceived(IoSession session, Object message) {
        for (IoFilter filter : chain) {
            message = filter.messageReceived(session, message);
        }
        return message;
    }

    @Override
    public Object processMessageWriting(IoSession session, Object message) {
        int len = chain.size();
        for (int i = 1; i <= len; i++) {
            message = chain.get(len - i).messageWriting(session, message);
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