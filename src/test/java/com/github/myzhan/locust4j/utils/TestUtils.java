package com.github.myzhan.locust4j.utils;

import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestUtils {

    @Test
    public void TestMD5() {
        assertEquals(Utils.md5("hello", "world"), "fc5e038d38a57032085441e7fe7010b0");
    }

    @Test
    public void TestGetHostname() throws IOException {
        // Test that getHostname returns a non-null, non-empty string
        String hostname = Utils.getHostname();
        assertNotNull("Hostname should not be null", hostname);
        assertFalse("Hostname should not be empty", hostname.isEmpty());
        
        // If we can get system hostname, verify they match
        // Otherwise, we should at least get "unknown" as fallback
        try {
            Process proc = Runtime.getRuntime().exec("hostname");
            java.io.InputStream is = proc.getInputStream();
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter(System.lineSeparator());
            if (s.hasNext()) {
                String systemHostname = s.next().trim();
                assertEquals("Hostname should match system hostname", systemHostname, hostname);
            }
            s.close();
            is.close();
        } catch (IOException e) {
            // If we can't get system hostname, just verify we got something
            assertTrue("Should return hostname or 'unknown'", 
                hostname.equals("unknown") || hostname.length() > 0);
        }
    }

    @Test
    public void TestGetNodeID() {
        String hostname = Utils.getHostname();
        assertTrue(Utils.getNodeID().matches(hostname + "_[a-f0-9]{32}$"));
    }

    @Test
    public void TestRound() {
        assertEquals(150, Utils.round(147, -1));
        assertEquals(Utils.round(3432, -2), 3400);
        assertEquals(Utils.round(58760, -3), Utils.round(58960, -3));
        assertEquals(Utils.round(58360, -3), Utils.round(58460, -3));
    }

    @Test
    public void TestGetSystemEnvWithDefault() {
        assertNotNull(Utils.getSystemEnvWithDefault("HOME", null));
        assertEquals("xxx", Utils.getSystemEnvWithDefault("not_found", "xxx"));
    }
}
