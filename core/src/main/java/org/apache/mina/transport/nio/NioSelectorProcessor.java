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

package org.apache.mina.transport.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLException;

import org.apache.mina.api.IdleStatus;
import org.apache.mina.api.IoServer;
import org.apache.mina.api.IoService;
import org.apache.mina.api.IoSession;
import org.apache.mina.api.RuntimeIoException;
import org.apache.mina.service.AbstractIoService;
import org.apache.mina.service.SelectorProcessor;
import org.apache.mina.service.SelectorStrategy;
import org.apache.mina.service.idlechecker.IdleChecker;
import org.apache.mina.service.idlechecker.IndexedIdleChecker;
import org.apache.mina.session.AbstractIoSession;
import org.apache.mina.transport.tcp.TcpSessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * A {@link SelectorProcessor} for processing NIO based {@link IoSession}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class NioSelectorProcessor implements SelectorProcessor<NioTcpServer, NioUdpServer> {

    /** A logger for this class */
    private static final Logger LOGGER = LoggerFactory.getLogger(NioSelectorProcessor.class);

    /**
     * A timeout used for the select, as we need to get out to deal with idle
     * sessions
     */
    private static final long SELECT_TIMEOUT = 1000L;

    private SelectorStrategy<NioSelectorProcessor> strategy;

    /** Read buffer for all the incoming bytes (default to 64Kb) */
    private final ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);

    /** the thread polling and processing the I/O events */
    private SelectorWorker worker = null;

    /** helper for detecting idleing sessions */
    private final IdleChecker idleChecker = new IndexedIdleChecker();

    /** A queue containing the servers to bind to this selector */
    private final Queue<IoServer> serversToAdd = new ConcurrentLinkedQueue<IoServer>();

    /** server to remove of the selector */
    private final Queue<IoServer> serversToRemove = new ConcurrentLinkedQueue<IoServer>();

    /**
     * new session freshly accepted, placed here for being added to the selector
     */
    private final Queue<NioTcpSession> sessionsToConnect = new ConcurrentLinkedQueue<NioTcpSession>();

    /** session to be removed of the selector */
    private final Queue<NioTcpSession> sessionsToClose = new ConcurrentLinkedQueue<NioTcpSession>();

    /** A queue used to store the sessions to be flushed */
    private final Queue<NioTcpSession> flushingSessions = new ConcurrentLinkedQueue<NioTcpSession>();

    private Selector selector;

    // Lock for Selector worker, using default. can look into fairness later.
    // We need to think about a lock less mechanism here.
    private final Lock workerLock = new ReentrantLock();

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void setStrategy(SelectorStrategy<?> strategy) {
        this.strategy = (SelectorStrategy<NioSelectorProcessor>) strategy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addServer(NioTcpServer server) {
        serversToAdd.add(server);
        wakeupWorker();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addServer(NioUdpServer server) {
        serversToAdd.add(server);
        wakeupWorker();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeServer(NioTcpServer server) {
        serversToRemove.add(server);
        wakeupWorker();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeServer(NioUdpServer server) {
        serversToRemove.add(server);
        wakeupWorker();
    }

    /**
     * Wake the I/O worker thread and if none exists, create a new one FIXME :
     * too much locking there ?
     */
    private void wakeupWorker() {
        workerLock.lock();
        try {
            if (worker == null) {
                worker = new SelectorWorker();
                worker.start();
            }
        } finally {
            workerLock.unlock();
        }

        if (selector != null) {
            selector.wakeup();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSession(final IoService service, final Object clientSocket) throws SSLException {
        LOGGER.debug("create session");
        final SocketChannel socketChannel = (SocketChannel) clientSocket;
        final TcpSessionConfig config = (TcpSessionConfig) service.getSessionConfig();
        final NioTcpSession session = new NioTcpSession(service, socketChannel,
                strategy.getSelectorForNewSession(this), idleChecker);

        try {
            socketChannel.configureBlocking(false);
        } catch (IOException e) {
            LOGGER.error("Unexpected exception, while configuring socket as non blocking", e);
            throw new RuntimeIoException("cannot configure socket as non-blocking", e);
        }
        // apply idle configuration
        session.getConfig().setIdleTimeInMillis(IdleStatus.READ_IDLE, config.getIdleTimeInMillis(IdleStatus.READ_IDLE));
        session.getConfig().setIdleTimeInMillis(IdleStatus.WRITE_IDLE,
                config.getIdleTimeInMillis(IdleStatus.WRITE_IDLE));

        // apply the default service socket configuration
        Boolean keepAlive = config.isKeepAlive();

        if (keepAlive != null) {
            session.getConfig().setKeepAlive(keepAlive);
        }

        Boolean oobInline = config.isOobInline();

        if (oobInline != null) {
            session.getConfig().setOobInline(oobInline);
        }

        Boolean reuseAddress = config.isReuseAddress();

        if (reuseAddress != null) {
            session.getConfig().setReuseAddress(reuseAddress);
        }

        Boolean tcpNoDelay = config.isTcpNoDelay();

        if (tcpNoDelay != null) {
            session.getConfig().setTcpNoDelay(tcpNoDelay);
        }

        Integer receiveBufferSize = config.getReceiveBufferSize();

        if (receiveBufferSize != null) {
            session.getConfig().setReceiveBufferSize(receiveBufferSize);
        }

        Integer sendBufferSize = config.getSendBufferSize();

        if (sendBufferSize != null) {
            session.getConfig().setSendBufferSize(sendBufferSize);
        }

        Integer trafficClass = config.getTrafficClass();

        if (trafficClass != null) {
            session.getConfig().setTrafficClass(trafficClass);
        }

        Integer soLinger = config.getSoLinger();

        if (soLinger != null) {
            session.getConfig().setSoLinger(soLinger);
        }

        // Set the secured flag if the service is to be used over SSL/TLS
        if (config.isSecured()) {
            session.initSecure(config.getSslContext());
        }

        // event session created
        session.processSessionCreated();

        // add the session to the queue for being added to the selector
        sessionsToConnect.add(session);
        wakeupWorker();
    }

    public IdleChecker getIdleChecker() {
        return idleChecker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush(final AbstractIoSession session) {
        LOGGER.debug("scheduling session {} for writing", session);
        // add the session to the list of session to be registered for writing
        flushingSessions.add((NioTcpSession) session);
        // wake the selector for unlocking the I/O thread
        wakeupWorker();
    }

    /** 
     * Add the session to list of session to close and remove
     * @param session
     */
    public void addSessionToClose(NioTcpSession session) {
        sessionsToClose.add(session);
    }

    public void cancelKeyForWritting(NioTcpSession session) {

        // a key registered for read ? (because we can have a
        // Selector for reads and another for the writes
        SelectionKey readKey = session.getSelectionKey();

        if (readKey != null) {
            LOGGER.debug("registering key for only reading");

            try {
                SelectionKey key = session.getSocketChannel().register(selector, SelectionKey.OP_READ, session);
                session.setSelectionKey(key);
            } catch (ClosedChannelException e) {
                LOGGER.error("already closed session", e);
            }
        } else {
            LOGGER.debug("cancel key for writing");
            session.getSocketChannel().keyFor(selector).cancel();
        }
    }

    /**
     * The worker processing incoming session creation, session destruction
     * requests, session write and reads. It will also bind new servers.
     */
    private class SelectorWorker extends Thread {

        public SelectorWorker() {
        }

        @Override
        public void run() {
            try {
                if (selector == null) {
                    LOGGER.debug("opening a new selector");

                    try {
                        selector = Selector.open();
                    } catch (IOException e) {
                        LOGGER.error("IOException while opening a new Selector", e);
                    }
                }

                for (;;) {
                    try {
                        // pop server sockets for removing
                        if (serversToRemove.size() > 0) {
                            processServerRemove();
                        }

                        // pop new server sockets for accepting
                        if (serversToAdd.size() > 0) {
                            processServerAdd();
                        }

                        // pop new session for starting read/write
                        if (sessionsToConnect.size() > 0) {
                            processConnectSessions();
                        }

                        // pop session for close, if any
                        if (sessionsToClose.size() > 0) {
                            processCloseSessions();
                        }

                        LOGGER.debug("selecting...");
                        int readyCount = selector.select(SELECT_TIMEOUT);
                        LOGGER.debug("... done selecting : {}", readyCount);

                        if (readyCount > 0) {
                            // process selected keys
                            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();

                            // Loop on each SelectionKey and process any valid
                            // action
                            while (selectedKeys.hasNext()) {
                                SelectionKey key = selectedKeys.next();
                                selectedKeys.remove();

                                if (!key.isValid()) {
                                    continue;
                                }

                                selector.selectedKeys().remove(key);

                                if (key.isAcceptable()) {
                                    ((SelectorEventListener) key.attachment()).acceptReady(NioSelectorProcessor.this);
                                }

                                if (key.isReadable()) {
                                    ((SelectorEventListener) key.attachment()).readReady(NioSelectorProcessor.this,
                                            readBuffer);
                                }

                                if (key.isWritable()) {
                                    ((SelectorEventListener) key.attachment()).writeReady(NioSelectorProcessor.this);
                                }

                            }
                        }

                        // registering session with data in the write queue for
                        // writing
                        while (!flushingSessions.isEmpty()) {
                            processFlushSessions();
                        }
                    } catch (IOException e) {
                        LOGGER.error("IOException while selecting selector", e);
                    }

                    // stop the worker if needed
                    workerLock.lock();

                    try {
                        if (selector.keys().isEmpty()) {
                            worker = null;
                            break;
                        }
                    } finally {
                        workerLock.unlock();
                    }

                    // check for idle events
                    idleChecker.processIdleSession(System.currentTimeMillis());
                }
            } catch (Exception e) {
                LOGGER.error("Unexpected exception : ", e);
            }
        }

        /**
         * Handles the servers addition
         */
        private void processServerAdd() throws IOException {
            IoServer server;

            while ((server = serversToAdd.poll()) != null) {
                if (server instanceof NioTcpServer) {
                    NioTcpServer tcpServer = (NioTcpServer) server;
                    // register for accept
                    SelectionKey key = tcpServer.getServerSocketChannel().register(selector, SelectionKey.OP_ACCEPT);
                    key.attach(tcpServer);
                    tcpServer.setAcceptKey(key);
                } else {
                    NioUdpServer udpServer = (NioUdpServer) server;
                    // register for read
                    SelectionKey key = udpServer.getDatagramChannel().register(selector, SelectionKey.OP_READ);
                    key.attach(udpServer);
                    udpServer.setReadKey(key);
                }

                LOGGER.debug("registered for accept : {}", server);
            }
        }

        /**
         * Handles the servers removal
         */
        private void processServerRemove() {
            IoServer server;

            while ((server = serversToRemove.poll()) != null) {
                if (server instanceof NioTcpServer) {
                    NioTcpServer tcpServer = (NioTcpServer) server;
                    // find the server key and cancel it
                    SelectionKey key = tcpServer.getAcceptKey();
                    key.cancel();
                    tcpServer.setAcceptKey(null);
                    key.attach(null);
                } else {
                    NioUdpServer udpServer = (NioUdpServer) server;
                    // find the server key and cancel it
                    SelectionKey key = udpServer.getReadKey();
                    key.cancel();
                    udpServer.setReadKey(null);
                    key.attach(null);
                }
            }
        }

        /**
         * Handles all the sessions that must be connected
         */
        private void processConnectSessions() throws IOException {
            while (!sessionsToConnect.isEmpty()) {
                NioTcpSession session = sessionsToConnect.poll();
                SelectionKey key = session.getSocketChannel().register(selector, SelectionKey.OP_READ);
                key.attach(session);

                session.setSelectionKey(key);

                // Switch to CONNECTED, only if the session is not secured, as
                // the SSL Handshake
                // will occur later.
                if (!session.isSecured()) {
                    session.setConnected();

                    // fire the event
                    ((AbstractIoService) session.getService()).fireSessionCreated(session);
                    session.processSessionOpened();
                    long time = System.currentTimeMillis();
                    idleChecker.sessionRead(session, time);
                    idleChecker.sessionWritten(session, time);
                }
            }
        }

        /**
         * Handles all the sessions that must be closed
         */
        private void processCloseSessions() throws IOException {
            while (!sessionsToClose.isEmpty()) {
                NioTcpSession session = sessionsToClose.poll();

                SelectionKey key = session.getSelectionKey();
                key.cancel();

                // closing underlying socket
                session.getSocketChannel().close();
                // fire the event
                session.processSessionClosed();
                ((AbstractIoService) session.getService()).fireSessionDestroyed(session);
            }
        }

        /**
         * Flushes the sessions
         */
        private void processFlushSessions() throws IOException {
            NioTcpSession session = null;

            while ((session = flushingSessions.poll()) != null) {
                // a key registered for read ? (because we can have a
                // Selector for reads and another for the writes
                SelectionKey readKey = session.getSelectionKey();

                if (readKey != null) {
                    // register for read/write
                    SelectionKey key = session.getSocketChannel().register(selector,
                            SelectionKey.OP_READ | SelectionKey.OP_WRITE, session);

                    session.setSelectionKey(key);
                } else {
                    session.getSocketChannel().register(selector, SelectionKey.OP_WRITE, session);
                }
            }
        }
    }
}
