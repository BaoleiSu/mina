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
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.IoFilter;
import org.apache.mina.IoFilterChain;

public class DefaultIoFilterChain implements IoFilterChain {

    /**
     * The list of {@link IoFilter} compounding this chain.
     * We use a {@link CopyOnWriteArrayList} because we want to read quickly (and thread safely) but we don't care much about chain
     * modification performances.
     */
    private List<IoFilter> chain = new CopyOnWriteArrayList<IoFilter>();

    //===================
    // CHAIN MANIPULATION
    //===================

    @Override
    public List<IoFilter> getAll() {
        // send a copy of the list
        return new ArrayList<IoFilter>(chain);
    }

    @Override
    public void addFirst(IoFilter ioFilter) {
        chain.add(0, ioFilter);
    }

    @Override
    public void addLast(IoFilter ioFilter) {
        chain.add(ioFilter);
    }

    @Override
    public boolean removeFilter(IoFilter ioFilter) {
        return chain.remove(ioFilter);
    }

    @Override
    public void insertBefore(int position, IoFilter ioFilter) throws IndexOutOfBoundsException {
        chain.add(position, ioFilter);
    }

    @Override
    public void insertAfter(int position, IoFilter ioFilter) throws IndexOutOfBoundsException {
        chain.add(position + 1, ioFilter);
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