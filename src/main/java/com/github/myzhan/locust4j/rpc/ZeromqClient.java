package com.github.myzhan.locust4j.rpc;

import java.io.IOException;

import com.github.myzhan.locust4j.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

/**
 * Locust used to support both plain-socket and zeromq.
 * Since Locust 0.8, it removes the plain-socket implementation.
 *
 * Locust4j only supports zeromq.
 *
 * @author myzhan
 */
public class ZeromqClient implements Client {

    private static final Logger logger = LoggerFactory.getLogger(ZeromqClient.class);

    private final ZMQ.Context context = ZMQ.context(1);
    private final String identity;
    private final ZMQ.Socket dealerSocket;
    /**
     * Lock for thread-safe access to the ZMQ socket.
     * ZMQ sockets are NOT thread-safe, so all recv() and send() operations
     * must be synchronized. This is especially important when virtual threads
     * are enabled, as many virtual threads may be trying to receive/send
     * messages concurrently through the same socket.
     */
    private final Object socketLock = new Object();

    public ZeromqClient(String host, int port, String nodeID) {
        this.identity = nodeID;
        this.dealerSocket = context.socket(ZMQ.DEALER);
        this.dealerSocket.setIdentity(this.identity.getBytes());
        boolean connected = this.dealerSocket.connect(String.format("tcp://%s:%d", host, port));
        if (connected) {
            logger.debug("Locust4j is connected to master({}:{})", host, port);
        } else {
            logger.debug("Locust4j isn't connected to master({}:{}), please check your network situation", host, port);
        }

    }

    @Override
    public Message recv() throws IOException {
        synchronized (socketLock) {
            try {
                byte[] bytes = this.dealerSocket.recv();
                return new Message(bytes);
            } catch (ZMQException ex) {
                throw new IOException("Failed to receive ZeroMQ message", ex);
            }
        }
    }

    @Override
    public void send(Message message) throws IOException {
        synchronized (socketLock) {
            try {
                byte[] bytes = message.getBytes();
                this.dealerSocket.send(bytes);
            } catch (ZMQException ex) {
                throw new IOException("Failed to send ZeroMQ message", ex);
            }
        }
    }

    @Override
    public void close() {
        synchronized (socketLock) {
            dealerSocket.close();
            context.close();
        }
    }
}

