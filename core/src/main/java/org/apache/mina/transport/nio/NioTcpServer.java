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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.apache.mina.api.IdleStatus;
import org.apache.mina.service.idlechecker.IdleChecker;
import org.apache.mina.service.idlechecker.IndexedIdleChecker;
import org.apache.mina.transport.tcp.AbstractTcpServer;
import org.apache.mina.transport.tcp.TcpSessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a TCP NIO based server.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class NioTcpServer extends AbstractTcpServer implements SelectorListener {
    static final Logger LOG = LoggerFactory.getLogger(NioTcpServer.class);

    // the bound local address
    private SocketAddress address = null;

    private final SelectorLoop acceptSelectorLoop;

    private final SelectorLoop readWriteSelectorLoop;

    // the key used for selecting accept event
    private SelectionKey acceptKey = null;

    // the server socket for accepting clients
    private ServerSocketChannel serverChannel = null;

    private final IdleChecker idleChecker = new IndexedIdleChecker();

    public NioTcpServer(final SelectorLoop acceptSelectorLoop, SelectorLoop readWriteSelectorLoop) {
        super();
        this.acceptSelectorLoop = acceptSelectorLoop;
        this.readWriteSelectorLoop = readWriteSelectorLoop;
    }

    /**
     * Get the inner Server socket for accepting new client connections
     * @return
     */
    public ServerSocketChannel getServerSocketChannel() {
        return this.serverChannel;
    }

    public void setServerSocketChannel(ServerSocketChannel serverChannel) {
        this.serverChannel = serverChannel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void bind(final SocketAddress localAddress) throws IOException {
        if (localAddress == null) {
            // We should at least have one address to bind on
            throw new IllegalArgumentException("LocalAdress cannot be null");
        }

        // check if the address is already bound
        if (this.address != null) {
            throw new IOException("address " + address + " already bound");
        }

        LOG.info("binding address {}", localAddress);
        this.address = localAddress;

        serverChannel = ServerSocketChannel.open();
        serverChannel.socket().setReuseAddress(isReuseAddress());
        serverChannel.socket().bind(address);
        serverChannel.configureBlocking(false);

        acceptSelectorLoop.register(true, false, false, this, serverChannel);

        // it's the first address bound, let's fire the event
        this.fireServiceActivated();

        // will start the selector processor if we are the first service
        acceptSelectorLoop.incrementServiceCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getBoundAddress() {
        return address;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void unbind() throws IOException {
        LOG.info("unbinding {}", address);
        if (this.address == null) {
            throw new IllegalStateException("server not bound");
        }
        serverChannel.socket().close();
        serverChannel.close();
        acceptSelectorLoop.unregister(this, serverChannel);

        this.address = null;
        this.fireServiceInactivated();

        // will stop the acceptor processor if we are the last service
        acceptSelectorLoop.decrementServiceCount();
    }

    /**
     * @return the acceptKey
     */
    public SelectionKey getAcceptKey() {
        return acceptKey;
    }

    /**
     * @param acceptKey the acceptKey to set
     */
    public void setAcceptKey(SelectionKey acceptKey) {
        this.acceptKey = acceptKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ready(boolean accept, boolean read, ByteBuffer readBuffer, boolean write) {
        if (accept) {
            LOG.debug("acceptable new client");

            // accepted connection
            try {
                LOG.debug("new client accepted");
                createSession(getServerSocketChannel().accept());

            } catch (IOException e) {
                LOG.error("error while accepting new client", e);
            }
        }
        if (read || write) {
            throw new IllegalStateException("should not receive read or write events");
        }
    }

    private void createSession(final SocketChannel clientSocket) throws IOException {
        LOG.debug("create session");
        final SocketChannel socketChannel = clientSocket;
        final TcpSessionConfig config = getSessionConfig();
        final NioTcpSession session = new NioTcpSession(this, socketChannel, readWriteSelectorLoop, idleChecker);

        socketChannel.configureBlocking(false);

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
        readWriteSelectorLoop.register(false, true, false, session, socketChannel);
        readWriteSelectorLoop.incrementServiceCount();
        session.processSessionOpened();
        session.setConnected();
    }

}