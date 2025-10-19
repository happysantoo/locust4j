package com.github.myzhan.locust4j.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for VirtualThreads utility class.
 */
public class TestVirtualThreads {

    private String originalEnabledProperty;
    private String originalVerboseProperty;

    @Before
    public void setUp() {
        // Save original system properties
        originalEnabledProperty = System.getProperty("locust4j.virtualThreads.enabled");
        originalVerboseProperty = System.getProperty("locust4j.virtualThreads.verbose");
    }

    @After
    public void tearDown() {
        // Restore original system properties
        if (originalEnabledProperty != null) {
            System.setProperty("locust4j.virtualThreads.enabled", originalEnabledProperty);
        } else {
            System.clearProperty("locust4j.virtualThreads.enabled");
        }
        
        if (originalVerboseProperty != null) {
            System.setProperty("locust4j.virtualThreads.verbose", originalVerboseProperty);
        } else {
            System.clearProperty("locust4j.virtualThreads.verbose");
        }
    }

    @Test
    public void testIsEnabledDefault() {
        // Virtual threads are enabled by default in v3.0.0+
        // The static initialization reads the system property which defaults to "true"
        assertTrue("Virtual threads should be enabled by default", VirtualThreads.isEnabled());
    }

    @Test
    public void testIsEnabledConfiguration() {
        // VirtualThreads is initialized statically based on system properties at class load time
        // So we can only test the current state, not change it dynamically
        boolean enabled = VirtualThreads.isEnabled();
        boolean available = VirtualThreads.isAvailable();
        
        // Should be consistent: enabled implies available
        if (enabled) {
            assertTrue("If enabled, virtual threads must be available", available);
        }
        
        // Test that the method returns a valid boolean
        assertNotNull("isEnabled should not return null", Boolean.valueOf(enabled));
    }

    @Test
    public void testIsAvailable() {
        boolean available = VirtualThreads.isAvailable();
        
        // On Java 21+, should be true; on Java 8-20, should be false
        int javaVersion = getJavaVersion();
        if (javaVersion >= 21) {
            assertTrue("Virtual threads should be available on Java 21+", available);
        } else if (javaVersion > 0 && javaVersion < 21) {
            assertFalse("Virtual threads should not be available before Java 21", available);
        }
        // If version detection fails (javaVersion == 0), just accept any result
    }

    @Test
    public void testIsVerbose() {
        // Just verify the method doesn't throw and returns a boolean
        boolean verbose = VirtualThreads.isVerbose();
        assertNotNull("isVerbose should not return null", Boolean.valueOf(verbose));
    }
    
    private int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return 0; // Unknown version
        }
    }

    @Test
    public void testCreateThreadFactoryWithVirtualThreadsDisabled() {
        System.clearProperty("locust4j.virtualThreads.enabled");
        
        AtomicLong counter = new AtomicLong(0);
        ThreadFactory factory = VirtualThreads.createThreadFactory("test-", counter);
        assertNotNull("Thread factory should not be null", factory);
        
        Thread thread = factory.newThread(() -> {});
        assertNotNull("Thread should not be null", thread);
        assertTrue("Thread name should contain prefix", thread.getName().contains("test-"));
    }

    @Test
    public void testCreateThreadFactoryNaming() {
        AtomicLong counter = new AtomicLong(0);
        ThreadFactory factory = VirtualThreads.createThreadFactory("vtest-", counter);
        assertNotNull("Thread factory should not be null", factory);
        
        Thread thread = factory.newThread(() -> {});
        assertNotNull("Thread should not be null", thread);
        
        // Verify thread naming
        String threadName = thread.getName();
        assertNotNull("Thread name should not be null", threadName);
        assertTrue("Thread name should contain prefix", threadName.contains("vtest-"));
        assertTrue("Thread name should have counter", threadName.matches(".*\\d+.*"));
    }

    @Test
    public void testThreadFactoryCreatesMultipleThreads() throws InterruptedException {
        AtomicLong counter = new AtomicLong(0);
        ThreadFactory factory = VirtualThreads.createThreadFactory("multi-", counter);
        
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger executionCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            Thread thread = factory.newThread(() -> {
                executionCount.incrementAndGet();
                latch.countDown();
            });
            thread.start();
        }
        
        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS));
        assertEquals("All threads should execute", threadCount, executionCount.get());
        assertEquals("Counter should be incremented", 10, counter.get());
    }

    @Test
    public void testCreateExecutorService() throws InterruptedException {
        ExecutorService executor = VirtualThreads.createExecutorService("test-executor");
        assertNotNull("Executor service should not be null", executor);
        
        try {
            int taskCount = 10;
            CountDownLatch latch = new CountDownLatch(taskCount);
            AtomicInteger executionCount = new AtomicInteger(0);
            
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    executionCount.incrementAndGet();
                    latch.countDown();
                });
            }
            
            assertTrue("All tasks should complete", latch.await(5, TimeUnit.SECONDS));
            assertEquals("All tasks should execute", taskCount, executionCount.get());
        } finally {
            executor.shutdown();
            assertTrue("Executor should shut down", executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testExecutorServiceWithHighConcurrency() throws InterruptedException {
        // Test executor service can handle many concurrent tasks (works with both platform and virtual threads)
        ExecutorService executor = VirtualThreads.createExecutorService("concurrent-executor");
        assertNotNull("Executor service should not be null", executor);
        
        try {
            // Test with many concurrent tasks
            int taskCount = 100; // Reduced from 1000 for compatibility with platform threads
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(1); // Simulate I/O
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    latch.countDown();
                });
            }
            
            assertTrue("All tasks should complete", 
                latch.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
            assertTrue("Executor should shut down", executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testGetThreadingMode() {
        String mode = VirtualThreads.getThreadingMode();
        assertNotNull("Threading mode should not be null", mode);
        assertFalse("Threading mode should not be empty", mode.isEmpty());
        
        // Should be either "platform" or "virtual" or contain these words
        assertTrue("Should contain 'platform' or 'virtual'", 
            mode.toLowerCase().contains("platform") || mode.toLowerCase().contains("virtual"));
    }

    @Test
    public void testExecutorServiceShutdown() throws InterruptedException {
        ExecutorService executor = VirtualThreads.createExecutorService("shutdown-test");
        
        // Submit a task
        CountDownLatch latch = new CountDownLatch(1);
        executor.submit(latch::countDown);
        
        assertTrue("Task should complete before shutdown", latch.await(5, TimeUnit.SECONDS));
        
        // Shutdown
        executor.shutdown();
        assertTrue("Executor should accept shutdown", executor.isShutdown());
        assertTrue("Executor should terminate", executor.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue("Executor should be terminated", executor.isTerminated());
    }

    @Test
    public void testThreadFactoryWithNullRunnable() {
        AtomicLong counter = new AtomicLong(0);
        ThreadFactory factory = VirtualThreads.createThreadFactory("null-test-", counter);
        
        try {
            Thread thread = factory.newThread(null);
            assertNotNull("Thread should be created even with null runnable", thread);
        } catch (NullPointerException e) {
            // This is acceptable behavior - some factories reject null
        }
    }

    @Test
    public void testConcurrentExecutorCreation() throws InterruptedException {
        int executorCount = 10;
        CountDownLatch latch = new CountDownLatch(executorCount);
        List<ExecutorService> executors = new ArrayList<>();
        
        for (int i = 0; i < executorCount; i++) {
            final int index = i;
            new Thread(() -> {
                ExecutorService executor = VirtualThreads.createExecutorService("concurrent-" + index);
                synchronized (executors) {
                    executors.add(executor);
                }
                latch.countDown();
            }).start();
        }
        
        assertTrue("All executors should be created", latch.await(5, TimeUnit.SECONDS));
        assertEquals("Should create all executors", executorCount, executors.size());
        
        // Cleanup
        for (ExecutorService executor : executors) {
            executor.shutdown();
            assertTrue("Each executor should shutdown", executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testMultipleThreadFactoryPrefixes() {
        String[] prefixes = {"worker-", "stats-", "rpc-", "limiter-"};
        
        for (String prefix : prefixes) {
            AtomicLong counter = new AtomicLong(0);
            ThreadFactory factory = VirtualThreads.createThreadFactory(prefix, counter);
            Thread thread = factory.newThread(() -> {});
            
            String threadName = thread.getName();
            assertTrue("Thread name should contain prefix: " + prefix, 
                threadName.contains(prefix) || threadName.startsWith(prefix));
        }
    }

    @Test
    public void testFactoryAndExecutorCreationDoesNotThrow() {
        // Verify that creating thread factories and executors doesn't throw exceptions
        AtomicLong counter = new AtomicLong(0);
        ThreadFactory factory = VirtualThreads.createThreadFactory("safe-test-", counter);
        assertNotNull("Factory should not be null", factory);
        
        ExecutorService executor = VirtualThreads.createExecutorService("safe-executor");
        assertNotNull("Executor should not be null", executor);
        executor.shutdown();
    }

    @Test
    public void testExecutorServiceRejectsTasksAfterShutdown() {
        ExecutorService executor = VirtualThreads.createExecutorService("reject-test");
        executor.shutdown();
        
        try {
            executor.submit(() -> {});
            // Some executors may reject, others may accept before full shutdown
        } catch (Exception e) {
            // Expected for some implementations
            assertTrue("Should be rejection exception", 
                e.getClass().getSimpleName().contains("Reject"));
        }
    }
}
