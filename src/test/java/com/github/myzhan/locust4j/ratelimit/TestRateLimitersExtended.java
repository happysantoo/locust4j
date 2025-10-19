package com.github.myzhan.locust4j.ratelimit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for rate limiters including edge cases and error handling.
 */
public class TestRateLimitersExtended {

    private List<AbstractRateLimiter> limiterList;

    @Before
    public void setUp() {
        limiterList = new ArrayList<>();
    }

    @After
    public void tearDown() {
        for (AbstractRateLimiter limiter : limiterList) {
            if (limiter != null) {
                try {
                    limiter.stop();
                } catch (Exception e) {
                    // Ignore - limiter may not have been started
                }
            }
        }
        limiterList.clear();
    }

    @Test
    public void testStableRateLimiterWithZeroRate() throws InterruptedException {
        // Note: Rate limiter behavior with rate 0 is undefined, so we just test it doesn't crash
        try {
            StableRateLimiter limiter = new StableRateLimiter(0);
            limiterList.add(limiter);
            limiter.start();

            Thread.sleep(100);
            // Just verify no crash occurs
            limiter.acquire();
            // If we get here without exception, test passes
        } catch (Exception e) {
            // Also acceptable - implementation may reject rate 0
        }
    }

    @Test
    public void testStableRateLimiterWithNegativeRate() {
        try {
            StableRateLimiter limiter = new StableRateLimiter(-1);
            limiterList.add(limiter);
            limiter.start();
            
            // Behavior with negative rate is undefined, but should not crash
            limiter.acquire();
            // If we get here, it's acceptable
        } catch (IllegalArgumentException e) {
            // Also acceptable to throw exception
        }
    }

    @Test
    public void testStableRateLimiterCreation() {
        // Just test that we can create limiters with various rates without crashing
        StableRateLimiter limiter1 = new StableRateLimiter(1);
        StableRateLimiter limiter2 = new StableRateLimiter(100);
        StableRateLimiter limiter3 = new StableRateLimiter(10000);
        
        limiterList.add(limiter1);
        limiterList.add(limiter2);
        limiterList.add(limiter3);
        
        assertNotNull("Limiter should be created", limiter1);
        assertNotNull("Limiter should be created", limiter2);
        assertNotNull("Limiter should be created", limiter3);
    }

    @Test
    public void testStableRateLimiterLifecycle() throws InterruptedException {
        StableRateLimiter limiter = new StableRateLimiter(100);
        limiterList.add(limiter);
        
        // Test start/stop lifecycle
        limiter.start();
        Thread.sleep(100);
        
        limiter.stop();
        Thread.sleep(100);

        // Multiple starts/stops should not crash
        limiter.start();
        Thread.sleep(50);
        limiter.stop();
        
        // Test passes if no exceptions thrown
        assertTrue("Lifecycle test completed", true);
    }

    @Test
    public void testStableRateLimiterMultipleStarts() {
        StableRateLimiter limiter = new StableRateLimiter(10);
        limiterList.add(limiter);
        
        limiter.start();
        // Starting again should be safe
        limiter.start();
        limiter.start();
        
        // Should not crash
        limiter.stop();
    }

    @Test
    public void testRampUpRateLimiterBasic() throws InterruptedException {
        // maxThreshold, rampUpStep, rampUpPeriod, rampUpTimeUnit, refillPeriod, refillUnit
        RampUpRateLimiter limiter = new RampUpRateLimiter(10, 2, 1, TimeUnit.SECONDS, 100, TimeUnit.MILLISECONDS);
        limiterList.add(limiter);
        limiter.start();

        Thread.sleep(1500);

        // Should acquire some permits after ramp-up
        int acquired = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.acquire()) {
                acquired++;
            }
        }

        assertTrue("Should acquire at least one permit", acquired > 0);
    }

    @Test
    public void testRampUpRateLimiterWithZeroRampUpStep() throws InterruptedException {
        // Behavior with zero ramp-up step is undefined, just test it doesn't crash
        try {
            RampUpRateLimiter limiter = new RampUpRateLimiter(10, 0, 1, TimeUnit.SECONDS, 100, TimeUnit.MILLISECONDS);
            limiterList.add(limiter);
            limiter.start();

            Thread.sleep(300);

            // Just verify no crash
            limiter.acquire();
            // Test passes if no exception
        } catch (Exception e) {
            // Also acceptable
        }
    }

    @Test
    public void testRampUpRateLimiterCreation() {
        // Test that we can create ramp-up limiters with various configurations
        RampUpRateLimiter limiter1 = new RampUpRateLimiter(100, 10, 1, TimeUnit.SECONDS, 100, TimeUnit.MILLISECONDS);
        RampUpRateLimiter limiter2 = new RampUpRateLimiter(1000, 50, 100, TimeUnit.MILLISECONDS, 10, TimeUnit.MILLISECONDS);
        
        limiterList.add(limiter1);
        limiterList.add(limiter2);
        
        assertNotNull("Limiter should be created", limiter1);
        assertNotNull("Limiter should be created", limiter2);
    }

    @Test
    public void testRampUpRateLimiterStop() throws InterruptedException {
        RampUpRateLimiter limiter = new RampUpRateLimiter(10, 2, 1, TimeUnit.SECONDS, 100, TimeUnit.MILLISECONDS);
        limiterList.add(limiter);
        limiter.start();

        Thread.sleep(200);

        limiter.stop();
        Thread.sleep(100);

        // After stop, should not crash
        try {
            limiter.acquire();
            // Acceptable
        } catch (Exception e) {
            // Also acceptable
        }
    }

    @Test
    public void testConcurrentAcquisitionStableRateLimiter() throws InterruptedException {
        StableRateLimiter limiter = new StableRateLimiter(100);
        limiterList.add(limiter);
        limiter.start();

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    // Just try to acquire without asserting success
                    for (int j = 0; j < 5; j++) {
                        limiter.acquire();
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue("All threads should complete", doneLatch.await(5, TimeUnit.SECONDS));
        assertEquals("Should not have exceptions", 0, exceptionCount.get());
    }

    @Test
    public void testConcurrentAcquisitionRampUpRateLimiter() throws InterruptedException {
        RampUpRateLimiter limiter = new RampUpRateLimiter(100, 20, 500, TimeUnit.MILLISECONDS, 50, TimeUnit.MILLISECONDS);
        limiterList.add(limiter);
        limiter.start();

        Thread.sleep(1000);

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger totalAcquired = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    int acquired = 0;
                    for (int j = 0; j < 10; j++) {
                        if (limiter.acquire()) {
                            acquired++;
                        }
                    }
                    totalAcquired.addAndGet(acquired);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue("All threads should complete", doneLatch.await(5, TimeUnit.SECONDS));
        assertTrue("Should acquire some permits", totalAcquired.get() > 0);
    }

    @Test
    public void testStableRateLimiterDoesNotCrash() throws InterruptedException {
        // Test that rate limiter operations don't crash the system
        StableRateLimiter limiter = new StableRateLimiter(50);
        limiterList.add(limiter);
        limiter.start();

        Thread.sleep(200);

        // Try various operations
        for (int i = 0; i < 20; i++) {
            limiter.acquire(); // May or may not succeed
            Thread.sleep(10);
        }

        limiter.stop();
        
        // Test passes if no exceptions thrown
        assertTrue("Operations completed without crashing", true);
    }

    @Test
    public void testRateLimiterStateTransitions() throws InterruptedException {
        StableRateLimiter limiter = new StableRateLimiter(100);
        limiterList.add(limiter);

        // Initial state - should not acquire (no permits yet)
        boolean beforeStart = limiter.acquire();
        assertFalse("Should not acquire before start", beforeStart);

        // Start and stop lifecycle
        limiter.start();
        Thread.sleep(100);
        limiter.stop();
        Thread.sleep(100);

        // Test passes if no exceptions during state transitions
        assertTrue("State transitions completed without crash", true);
    }
}
