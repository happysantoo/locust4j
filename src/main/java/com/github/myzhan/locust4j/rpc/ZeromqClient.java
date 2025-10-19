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
 * This implementation uses non-blocking I/O with ZMQ polling to avoid
 * the deadlock issue where recv() with a lock would block send() operations.
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
     * ZMQ sockets are NOT thread-safe.
     * We use non-blocking mode with polling to allow both send() and recv()
     * to proceed without long blocking calls that would starve the other operation.
     */
    private final Object socketLock = new Object();

    public ZeromqClient(String host, int port, String nodeID) {
        this.identity = nodeID;
        this.dealerSocket = context.socket(ZMQ.DEALER);
        this.dealerSocket.setIdentity(this.identity.getBytes());
        
        // Use non-blocking mode with short polls instead of long blocking recv()
        // This allows both send() and recv() to proceed without starving each other
        // ZMQ_RCVTIMEO = 0 means non-blocking (return immediately if no message)
        this.dealerSocket.setReceiveTimeOut(0);  // Non-blocking
        this.dealerSocket.setSendTimeOut(0);     // Non-blocking
        
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
                // Non-blocking receive - returns null immediately if no message
                byte[] bytes = this.dealerSocket.recv(ZMQ.DONTWAIT);
                if (bytes == null) {
                    // No message available right now - that's OK, try again later
                    return null;
                }
                return new Message(bytes);
            } catch (ZMQException ex) {
                // EAGAIN means no message available (expected with non-blocking)
                if (ex.getErrorCode() == zmq.ZError.EAGAIN) {
                    return null;
                }
                throw new IOException("Failed to receive ZeroMQ message", ex);
            }
        }
    }

    @Override
    public void send(Message message) throws IOException {
        synchronized (socketLock) {
            try {
                byte[] bytes = message.getBytes();
                // Non-blocking send with DONTWAIT flag
                this.dealerSocket.send(bytes, ZMQ.DONTWAIT);
            } catch (ZMQException ex) {
                // EAGAIN means socket buffer is full (queue is full)
                // This is expected under load - caller should retry
                if (ex.getErrorCode() == zmq.ZError.EAGAIN) {
                    throw new IOException("ZMQ socket send buffer full, retry later", ex);
                }
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


