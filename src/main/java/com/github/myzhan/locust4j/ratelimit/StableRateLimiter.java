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
 * A {@link StableRateLimiter} distributes permits at a configurable rate.
 * Each {@link #acquire()} blocks until a permit is available.
 *
 * @author myzhan
 * @since 1.0.3
 */
public class StableRateLimiter extends AbstractRateLimiter implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(StableRateLimiter.class);

    private final long maxThreshold;
    private final AtomicLong threshold;
    private final long period;
    private final TimeUnit unit;
    private ScheduledExecutorService updateTimer;
    private final AtomicBoolean stopped;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public StableRateLimiter(long maxThreshold) {
        this(maxThreshold, 1, TimeUnit.SECONDS);
    }

    public StableRateLimiter(long maxThreshold, long period, TimeUnit unit) {
        this.maxThreshold = maxThreshold;
        this.threshold = new AtomicLong(maxThreshold);
        this.period = period;
        this.unit = unit;
        this.stopped = new AtomicBoolean(true);
    }

    @Override
    public void start() {
        logger.debug("Starting StableRateLimiter with max threshold: {} per {} {}", 
                maxThreshold, period, unit);
        
        ThreadFactory factory;
        if (VirtualThreads.isEnabled()) {
            factory = VirtualThreads.createThreadFactory("StableRateLimiter-bucket-updater-", 
                    new AtomicLong(0));
            logger.debug("Using virtual threads for rate limiter");
        } else {
            factory = new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("StableRateLimiter-bucket-updater");
                    return thread;
                }
            };
        }
        
        updateTimer = new ScheduledThreadPoolExecutor(1, factory);
        updateTimer.scheduleAtFixedRate(this, 0, period, unit);
        stopped.set(false);
        logger.debug("StableRateLimiter started successfully");
    }

    @Override
    public void run() {
        // NOTICE: this method is invoked in a thread pool, make sure it throws no exceptions.
        lock.lock();
        try {
            this.threshold.set(maxThreshold);
            condition.signalAll();
        } finally {
            lock.unlock();
        }
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
        updateTimer.shutdownNow();
        stopped.set(true);
    }

    @Override
    public boolean isStopped() {
        return stopped.get();
    }
}
