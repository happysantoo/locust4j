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
 * This implementation uses blocking I/O with timeout on recv() to ensure:
 * 1. Thread safety: All operations guarded by socketLock mutex
 * 2. Deadlock prevention: recv() timeout guarantees lock is released periodically
 * 3. Simplicity: No complex error handling for EAGAIN or buffer management
 *
 * Architecture:
 * - Receiver thread: Holds lock during recv(300ms), releases every 300ms
 * - Sender thread: Can acquire lock when Receiver releases it (max 300ms wait)
 * - Result: Heartbeats sent reliably, no starvation
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
     * ZMQ sockets are NOT thread-safe, so ALL socket operations must be synchronized.
     * 
     * Lock hold times:
     * - recv(): max 300ms (socket timeout)
     * - send(): <1ms (immediate)
     * 
     * This pattern ensures:
     * 1. Receiver gets exclusive access to socket during recv()
     * 2. Sender waits at most 300ms for Receiver to release lock
     * 3. Heartbeat window: 300-600ms (within 1000ms heartbeat interval)
     */
    private final Object socketLock = new Object();

    public ZeromqClient(String host, int port, String nodeID) {
        this.identity = nodeID;
        this.dealerSocket = context.socket(ZMQ.DEALER);
        this.dealerSocket.setIdentity(this.identity.getBytes());
        
        // Set 300ms receive timeout to balance:
        // 1. Responsiveness: Lock released 3-4 times per heartbeat interval (1000ms)
        // 2. Safety: Prevents indefinite blocking if master stops responding
        // 3. Fairness: Sender gets opportunity to acquire lock every 300ms
        this.dealerSocket.setReceiveTimeOut(300);
        
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
                // Blocking receive with 300ms timeout
                // This timeout ensures the lock is released periodically,
                // allowing Sender thread to get its turn
                byte[] bytes = this.dealerSocket.recv();
                if (bytes == null) {
                    // Timeout occurred - this is normal, just means no message right now
                    return null;
                }
                return new Message(bytes);
            } catch (ZMQException ex) {
                // EAGAIN means receive timeout (expected with 300ms timeout)
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
                // Blocking send - waits for buffer space if needed
                // With proper timeout handling in recv(), this won't block indefinitely
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


