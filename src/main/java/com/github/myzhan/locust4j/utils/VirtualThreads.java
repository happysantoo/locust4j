package com.github.myzhan.locust4j.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for managing virtual threads configuration and creation.
 * Virtual threads are ENABLED by default on Java 21+, with opt-out support for platform threads.
 * Provides comprehensive logging and automatic fallback handling.
 *
 * @author locust4j
 * @since 3.0.0
 */
public class VirtualThreads {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreads.class);

    /**
     * System property to enable/disable virtual threads. Defaults to "true" (enabled).
     * Set to "false" to use platform threads instead.
     */
    public static final String ENABLE_VIRTUAL_THREADS_PROPERTY = "locust4j.virtualThreads.enabled";

    /**
     * System property to enable detailed virtual thread logging.
     */
    public static final String VIRTUAL_THREADS_VERBOSE_PROPERTY = "locust4j.virtualThreads.verbose";

    private static final boolean VIRTUAL_THREADS_ENABLED;
    private static final boolean VIRTUAL_THREADS_VERBOSE;
    private static final boolean VIRTUAL_THREADS_AVAILABLE;

    static {
        // Virtual threads are always available since we require Java 21+
        VIRTUAL_THREADS_AVAILABLE = true;

        // Virtual threads are ENABLED by default, but can be disabled via system property
        String enabledProperty = Utils.getSystemEnvWithDefault(ENABLE_VIRTUAL_THREADS_PROPERTY, "true");
        VIRTUAL_THREADS_ENABLED = Boolean.parseBoolean(enabledProperty);

        // Check verbose logging
        String verboseProperty = Utils.getSystemEnvWithDefault(VIRTUAL_THREADS_VERBOSE_PROPERTY, "false");
        VIRTUAL_THREADS_VERBOSE = Boolean.parseBoolean(verboseProperty);

        // Log initialization
        if (VIRTUAL_THREADS_ENABLED) {
            logger.info("Virtual threads are ENABLED (default). Running on Java {} with virtual threads support.",
                    System.getProperty("java.version"));
            logger.info("Virtual threads will be used for worker pool, stats processing, and communication threads.");
            logger.info("To disable virtual threads and use platform threads, set -D{}=false",
                    ENABLE_VIRTUAL_THREADS_PROPERTY);
            if (VIRTUAL_THREADS_VERBOSE) {
                logger.info("Verbose virtual thread logging is ENABLED.");
            }
        } else {
            logger.info("Virtual threads are DISABLED via system property. Using platform threads.");
            logger.info("Running on Java {} with platform threads.", System.getProperty("java.version"));
        }
    }



    /**
     * Check if virtual threads are enabled for this application.
     *
     * @return true if virtual threads are enabled and available
     */
    public static boolean isEnabled() {
        return VIRTUAL_THREADS_AVAILABLE && VIRTUAL_THREADS_ENABLED;
    }

    /**
     * Check if virtual threads are available on this JVM.
     * Always returns true since Java 21+ is required.
     *
     * @return true (always, since Java 21+ is required)
     */
    public static boolean isAvailable() {
        return VIRTUAL_THREADS_AVAILABLE;
    }

    /**
     * Check if verbose logging is enabled for virtual threads.
     *
     * @return true if verbose logging is enabled
     */
    public static boolean isVerbose() {
        return VIRTUAL_THREADS_VERBOSE;
    }

    /**
     * Create a ThreadFactory that creates virtual threads if enabled, otherwise platform threads.
     *
     * @param namePrefix prefix for thread names
     * @param counter    atomic counter for thread numbering
     * @return ThreadFactory instance
     */
    public static ThreadFactory createThreadFactory(String namePrefix, AtomicLong counter) {
        if (isEnabled()) {
            if (isVerbose()) {
                logger.debug("Creating virtual thread factory with prefix: {}", namePrefix);
            }
            return createVirtualThreadFactory(namePrefix, counter);
        } else {
            if (isVerbose()) {
                logger.debug("Creating platform thread factory with prefix: {}", namePrefix);
            }
            return createPlatformThreadFactory(namePrefix, counter);
        }
    }

    /**
     * Create a virtual thread factory.
     *
     * @param namePrefix prefix for thread names
     * @param counter    atomic counter for thread numbering
     * @return ThreadFactory that creates virtual threads
     */
    private static ThreadFactory createVirtualThreadFactory(String namePrefix, AtomicLong counter) {
        return r -> {
            long threadId = counter.getAndIncrement();
            String threadName = namePrefix + threadId;

            // Use Java 21+ virtual threads API directly
            Thread virtualThread = Thread.ofVirtual()
                    .name(threadName)
                    .unstarted(r);

            if (isVerbose()) {
                logger.trace("Created virtual thread: {}", threadName);
            }

            return virtualThread;
        };
    }

    /**
     * Create a platform thread factory.
     *
     * @param namePrefix prefix for thread names
     * @param counter    atomic counter for thread numbering
     * @return ThreadFactory that creates platform threads
     */
    private static ThreadFactory createPlatformThreadFactory(String namePrefix, AtomicLong counter) {
        return r -> {
            long threadId = counter.getAndIncrement();
            String threadName = namePrefix + threadId;
            return createPlatformThread(r, threadName);
        };
    }

    /**
     * Create a platform thread.
     *
     * @param r          runnable to execute
     * @param threadName name for the thread
     * @return platform Thread instance
     */
    private static Thread createPlatformThread(Runnable r, String threadName) {
        Thread thread = new Thread(r);
        thread.setName(threadName);

        if (isVerbose()) {
            logger.trace("Created platform thread: {}", threadName);
        }

        return thread;
    }

    /**
     * Create an ExecutorService using virtual threads if enabled, otherwise a cached thread pool.
     *
     * @param namePrefix prefix for executor thread names
     * @return ExecutorService instance
     */
    public static ExecutorService createExecutorService(String namePrefix) {
        if (isEnabled()) {
            logger.debug("Creating virtual thread executor service: {}", namePrefix);
            return createVirtualThreadExecutor(namePrefix);
        } else {
            logger.debug("Creating platform thread executor service: {}", namePrefix);
            return createPlatformThreadExecutor(namePrefix);
        }
    }

    /**
     * Create an ExecutorService backed by virtual threads.
     *
     * @param namePrefix prefix for thread names
     * @return ExecutorService using virtual threads
     */
    private static ExecutorService createVirtualThreadExecutor(String namePrefix) {
        AtomicLong counter = new AtomicLong(0);
        ThreadFactory factory = createVirtualThreadFactory(namePrefix, counter);
        
        // Use Java 21+ API directly
        ExecutorService executor = Executors.newThreadPerTaskExecutor(factory);

        logger.info("Successfully created virtual thread executor: {}", namePrefix);
        return executor;
    }

    /**
     * Create an ExecutorService backed by platform threads.
     *
     * @param namePrefix prefix for thread names
     * @return ExecutorService using platform threads
     */
    private static ExecutorService createPlatformThreadExecutor(String namePrefix) {
        AtomicLong counter = new AtomicLong(0);
        ThreadFactory factory = createPlatformThreadFactory(namePrefix, counter);
        return Executors.newCachedThreadPool(factory);
    }

    /**
     * Log virtual thread statistics for monitoring and debugging.
     *
     * @param context    context description (e.g., "Worker Pool", "Stats Processing")
     * @param threadCount number of threads
     */
    public static void logThreadStats(String context, long threadCount) {
        if (isVerbose()) {
            String threadType = isEnabled() ? "virtual" : "platform";
            logger.debug("[{}] Active {} threads: {}", context, threadType, threadCount);
        }
    }

    /**
     * Log an error related to virtual thread operations.
     *
     * @param context context description
     * @param message error message
     * @param throwable exception
     */
    public static void logError(String context, String message, Throwable throwable) {
        logger.error("[Virtual Threads - {}] {}", context, message, throwable);
    }

    /**
     * Get a description of the current threading mode.
     *
     * @return description string
     */
    public static String getThreadingMode() {
        if (isEnabled()) {
            return String.format("Virtual Threads (Java %s)", System.getProperty("java.version"));
        } else {
            return String.format("Platform Threads (Virtual threads disabled via system property, Java %s)",
                    System.getProperty("java.version"));
        }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private VirtualThreads() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
