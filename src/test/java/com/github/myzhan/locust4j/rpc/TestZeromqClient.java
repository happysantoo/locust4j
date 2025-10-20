package com.github.myzhan.locust4j.rpc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.github.myzhan.locust4j.message.Message;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author myzhan
 */
public class TestZeromqClient {

    @Test
    public void TestPingPong() throws Exception {
        // randomized the port to avoid conflicts
        int masterPort = ThreadLocalRandom.current().nextInt(10000) + 20000;

        TestServer server = null;
        Client client = null;
        
        try {
            server = new TestServer("127.0.0.1", masterPort);
            server.start();
            
            // Give server time to bind
            Thread.sleep(200);

            client = new ZeromqClient("127.0.0.1", masterPort, "testClient");
            Map<String, Object> data = new HashMap<>();
            data.put("hello", "world");

            client.send(new Message("test", data, -1, "node"));
            
            // With blocking recv() and 300ms timeout, message should arrive quickly
            Message message = client.recv();

            assertNotNull("Received message should not be null", message);
            assertEquals("Message type should match", "test", message.getType());
            assertEquals("Node ID should match", "node", message.getNodeID());
            assertEquals("Message data should match", data, message.getData());
        } finally {
            // Ensure cleanup happens even if test fails
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
            if (server != null) {
                try {
                    Thread.sleep(100);
                    server.stop();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }
}
