package com.github.myzhan.locust4j.ratelimit;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.github.myzhan.locust4j.utils.VirtualThreads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link RampUpRateLimiter} distributes permits at a ramp-up rate, in steps.
 * Each {@link #acquire()} blocks until a permit is available.
 *
 * @author myzhan
 * @since 1.0.4
 */
public class RampUpRateLimiter extends AbstractRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RampUpRateLimiter.class);

    private final long maxThreshold;
    private final AtomicLong nextThreshold;
    private final AtomicLong threshold;

    private final long rampUpStep;
    private final long rampUpPeriod;
    private final TimeUnit rampUpTimeUnit;

    private final long refillPeriod;
    private final TimeUnit refillUnit;

    private ScheduledExecutorService bucketUpdater;
    private ScheduledExecutorService thresholdUpdater;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicBoolean stopped;

    /**
     * Creates a {@code RampUpRateLimiter}
     *
     * @param maxThreshold   the max threshold that should not be overstepped.
     * @param rampUpStep     the ramp-up step.
     * @param rampUpPeriod   the duration of the period where the {@code RampUpRateLimiter} ramps up the threshold.
     * @param rampUpTimeUnit the time unit.
     * @param refillPeriod   the duration of the period where the {@code RampUpRateLimiter} updates the bucket.
     * @param refillUnit     the time unit.
     */
    public RampUpRateLimiter(long maxThreshold, long rampUpStep, long rampUpPeriod, TimeUnit rampUpTimeUnit,
                             long refillPeriod, TimeUnit refillUnit) {
        this.maxThreshold = maxThreshold;
        this.threshold = new AtomicLong(0);
        this.nextThreshold = new AtomicLong(0);
        this.rampUpStep = rampUpStep;
        this.rampUpPeriod = rampUpPeriod;
        this.rampUpTimeUnit = rampUpTimeUnit;
        this.refillPeriod = refillPeriod;
        this.refillUnit = refillUnit;
        this.stopped = new AtomicBoolean(true);
    }

    @Override
    public void start() {
        logger.debug("Starting RampUpRateLimiter with max threshold: {}, ramp-up step: {} per {} {}", 
                maxThreshold, rampUpStep, rampUpPeriod, rampUpTimeUnit);
        
        ThreadFactory thresholdFactory;
        ThreadFactory bucketFactory;
        
        if (VirtualThreads.isEnabled()) {
            thresholdFactory = VirtualThreads.createThreadFactory("RampUpRateLimiter-threshold-updater-", 
                    new AtomicLong(0));
            bucketFactory = VirtualThreads.createThreadFactory("RampUpRateLimiter-bucket-updater-", 
                    new AtomicLong(0));
            logger.debug("Using virtual threads for ramp-up rate limiter");
        } else {
            thresholdFactory = new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("RampUpRateLimiter-threshold-updater");
                    return thread;
                }
            };
            bucketFactory = new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("RampUpRateLimiter-bucket-updater");
                    return thread;
                }
            };
        }
        
        thresholdUpdater = new ScheduledThreadPoolExecutor(1, thresholdFactory);
        thresholdUpdater.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long nextValue = nextThreshold.get() + rampUpStep;
                if (nextValue < 0) {
                    // long value overflow
                    nextValue = Long.MAX_VALUE;
                }
                if (nextValue > maxThreshold) {
                    nextValue = maxThreshold;
                }
                nextThreshold.set(nextValue);
            }
        }, 0, rampUpPeriod, rampUpTimeUnit);

        bucketUpdater = new ScheduledThreadPoolExecutor(1, bucketFactory);
        bucketUpdater.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                try {
                    threshold.set(nextThreshold.get());
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }, 0, refillPeriod, refillUnit);

        stopped.set(false);
        logger.debug("RampUpRateLimiter started successfully");
    }

    @Override
    public boolean acquire() {
        long permit = this.threshold.decrementAndGet();
        if (permit < 0) {
            lock.lock();
            try {
                condition.await();
            } catch (InterruptedException ex) {
                logger.error("The process of acquiring a permit from rate limiter was interrupted", ex);
                Thread.currentThread().interrupt(); // Restore interrupted status
            } finally {
                lock.unlock();
            }
            return true;
        }
        return false;
    }

    @Override
    public void stop() {
        bucketUpdater.shutdownNow();
        thresholdUpdater.shutdownNow();
        stopped.set(true);
    }

    @Override
    public boolean isStopped() {
        return stopped.get();
    }
}
