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
package filterchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.mina.api.IdleStatus;
import org.apache.mina.api.IoFilter;
import org.apache.mina.api.IoSession;
import org.apache.mina.filterchain.DefaultIoFilterChain;
import org.junit.Test;

public class DefaultIoFilterChainTest {

    @Test
    public void testChainModification() {
        DefaultIoFilterChain filterChain = new DefaultIoFilterChain();

        DummyFilter filterA = new DummyFilter("A");
        DummyFilter filterB = new DummyFilter("B");
        DummyFilter filterC = new DummyFilter("C");

        // add two filters
        filterChain.addFirst(filterB);
        filterChain.addFirst(filterA);
        System.out.println(filterChain);
        assertEquals(filterChain.getAll().size(), 2);
        List<IoFilter> l = filterChain.getAll();

        assertEquals(l.get(0), filterA);
        assertEquals(l.get(1), filterB);

        assertTrue(filterChain.removeFilter(filterB));
        assertEquals(filterChain.getAll().size(), 1);
        assertFalse(filterChain.removeFilter(filterB));
        assertEquals(filterChain.getAll().size(), 1);
        assertTrue(filterChain.removeFilter(filterA));
        assertTrue(filterChain.getAll().isEmpty());

        // add three filter
        filterChain.addLast(filterB);
        filterChain.addLast(filterC);
        filterChain.addFirst(filterA);
        assertEquals(filterChain.getAll().size(), 3);
        l = filterChain.getAll();

        assertEquals(l.get(0), filterA);
        assertEquals(l.get(1), filterB);
        assertEquals(l.get(2), filterC);

        filterChain.removeFilter(filterB);
        assertEquals(filterChain.getAll().size(), 2);
        l = filterChain.getAll();
        assertEquals(l.get(0), filterA);
        assertEquals(l.get(1), filterC);

        filterChain.insertAfter(0, filterB);
        assertEquals(filterChain.getAll().size(), 3);
        l = filterChain.getAll();

        assertEquals(l.get(0), filterA);
        assertEquals(l.get(1), filterB);
        assertEquals(l.get(2), filterC);
        filterChain.removeFilter(filterB);

        filterChain.insertBefore(1, filterB);

        assertEquals(filterChain.getAll().size(), 3);
        l = filterChain.getAll();

        assertEquals(l.get(0), filterA);
        assertEquals(l.get(1), filterB);
        assertEquals(l.get(2), filterC);

    }

    private class DummyFilter implements IoFilter {

        String id;

        public DummyFilter(String id) {
            this.id = id;
        }

        @Override
        public void sessionCreated(IoSession session) throws Exception {
        }

        @Override
        public void sessionOpened(IoSession session) throws Exception {
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
        }

        @Override
        public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        }

        @Override
        public Object messageReceived(IoSession session, Object message) throws Exception {
            return message;
        }

        @Override
        public Object messageWriting(IoSession session, Object message) throws Exception {
            return message;
        }

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        }

        public String toString() {
            return "DummyFilter(" + id + ")";
        }
    }

}
