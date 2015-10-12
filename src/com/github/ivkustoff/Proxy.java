package com.github.ivkustoff;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Proxy implements Runnable {
    private final int BUFFER_SIZE = 2048;
    private final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    private SocketAddress remote;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private ServerSocket serverSocket;
    private Map<SocketChannel, SocketEntity> readMap = new HashMap<>();
    private Map<SocketChannel, SocketEntity> flushMap = new HashMap<>();


    public Proxy(final InetSocketAddress local, final InetSocketAddress target) {
        this.remote = target;
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocket = serverSocketChannel.socket();
            serverSocket.bind(local);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException ex) {
            closeAll();
        }
    }

    private void closeAll() {
        closeQuietly(serverSocket);
        closeQuietly(serverSocketChannel);
        closeQuietly(selector);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ex) {
                System.out.println(ex);
            }
        }
    }

    private void forwardSelectionKey(SelectionKey key) {
        if (key.isValid()) {
            if (key.isAcceptable()) {
                accept(key);
                return;
            }
            if (key.isReadable()) {
                readFromSource((SocketChannel) key.channel());
                return;
            }
            if (key.isWritable()) {
                flushBuffer((SocketChannel) key.channel());
                return;
            }
        }
    }

    private void readFromSource(SocketChannel channel) {
        SocketEntity entity = readMap.get(channel);
        if (entity != null) {
            try {
                entity.source.read(entity.buffer);
                writeToDestination(entity);
            } catch (IOException ex) {
                System.out.println("Exception while reading channel for proxy on port " + serverSocket.getLocalPort() + " " + ex);
                closeEntity(entity);
            }
        }
    }

    private void writeToDestination(final SocketEntity entity) throws IOException {
        final ByteBuffer buffer = entity.buffer;
        buffer.flip();
        entity.destination.write(buffer);
        if (buffer.hasRemaining()) {
            entity.source.register(selector, 0);
            buffer.compact();
            entity.destination.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            flushMap.put(entity.destination, entity);
        } else {
            buffer.clear();
        }
    }

    public void run() {
        while (selector.isOpen()) {
            try {
                final int ready = selector.select(TIMEOUT);
                if (ready != 0) {
                    Set<SelectionKey> toRemove = new HashSet<>();
                    for (SelectionKey key : selector.selectedKeys()) {
                        forwardSelectionKey(key);
                        toRemove.add(key);
                    }
                    selector.selectedKeys().removeAll(toRemove);
                }
            } catch (Exception ex) {
                System.out.println("Proxy on port" + serverSocket.getLocalPort() + " failed to work");
                closeAll();
                readMap.clear();
            }
        }
    }

    private void closeEntity(final SocketEntity entity) {
        readMap.remove(entity.source);
        readMap.remove(entity.destination);
        flushMap.remove(entity.source);
        flushMap.remove(entity.destination);
    }

    private void flushBuffer(SocketChannel channel) {
        SocketEntity entity = flushMap.get(channel);
        if (entity != null) {
            ByteBuffer buffer = entity.buffer;
            buffer.flip();
            try {
                entity.destination.write(buffer);
                if (buffer.hasRemaining()) {
                    buffer.compact();
                    return;
                }
                buffer.clear();
                entity.destination.register(selector, SelectionKey.OP_READ);
                flushMap.remove(entity.destination);
                entity.source.register(selector, SelectionKey.OP_READ);
            } catch (IOException e) {
                System.out.println("exception while trying to flush buffer " + e);
                closeEntity(entity);
            }
        }
    }

    private void accept(SelectionKey key) {
        final SocketChannel incoming = incomingChannel((ServerSocketChannel) key.channel());
        if (incoming != null) {
            final SocketChannel out = outgoingChannel();
            if (out == null) {
                closeQuietly(incoming);
                return;
            }
            readMap.put(incoming, new SocketEntity(ByteBuffer.allocate(BUFFER_SIZE), incoming, out));
            readMap.put(out, new SocketEntity(ByteBuffer.allocate(BUFFER_SIZE), out, incoming));

        }

    }

    private SocketChannel outgoingChannel() {
        try {
            SocketChannel outChannel = SocketChannel.open();
            outChannel.configureBlocking(false);
            outChannel.connect(remote);
            while (!outChannel.finishConnect()) { }
            outChannel.register(selector, SelectionKey.OP_READ);
            return outChannel;
        } catch (ClosedSelectorException | IOException e) {
            System.out.println("Outgoing connection has failed " + e);
            return null;
        }
    }

    private SocketChannel incomingChannel(ServerSocketChannel channel) {
        try {
            final SocketChannel inChannel = channel.accept();
            inChannel.configureBlocking(false);
            inChannel.register(selector, SelectionKey.OP_READ);
            return inChannel;
        } catch (ClosedSelectorException | IOException e) {
            System.out.println("Incoming connection has failed " + e);
            return null;
        }
    }

    static class SocketEntity {
        final ByteBuffer buffer;
        final SocketChannel source;
        final SocketChannel destination;
        public SocketEntity(ByteBuffer buffer, SocketChannel source, SocketChannel destination) {
            this.buffer = buffer;
            this.source = source;
            this.destination = destination;
        }
    }
}