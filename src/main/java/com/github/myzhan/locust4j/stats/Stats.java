package com.github.myzhan.locust4j.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.github.myzhan.locust4j.utils.Utils;
import com.github.myzhan.locust4j.utils.VirtualThreads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stats collects test results from reportSuccessQueue and reportFailureQueue and reports to Runner every 3 seconds.
 *
 * @author myzhan
 */
public class Stats implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Stats.class);

    private Map<String, StatsEntry> entries;
    private Map<String, StatsError> errors;
    private StatsEntry total;

    private final ConcurrentLinkedQueue<RequestSuccess> reportSuccessQueue;
    private final ConcurrentLinkedQueue<RequestFailure> reportFailureQueue;
    private final ConcurrentLinkedQueue<Boolean> clearStatsQueue;
    private final ConcurrentLinkedQueue<Boolean> timeToReportQueue;
    private final BlockingQueue<Map<String, Object>> messageToRunnerQueue;

    private ExecutorService threadPool;
    private final AtomicInteger threadNumber;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    /**
     * Probably, you don't need to create Stats unless you are writing unit tests.
     */
    public Stats() {
        reportSuccessQueue = new ConcurrentLinkedQueue<>();
        reportFailureQueue = new ConcurrentLinkedQueue<>();
        clearStatsQueue = new ConcurrentLinkedQueue<>();
        timeToReportQueue = new ConcurrentLinkedQueue<>();
        messageToRunnerQueue = new LinkedBlockingDeque<>();
        threadNumber = new AtomicInteger();

        this.entries = new HashMap<>(8);
        this.errors = new HashMap<>(8);
        this.total = new StatsEntry("Total");
        this.total.reset();
    }

    public static Stats getInstance() {
        return StatsInstanceHolder.INSTANCE;
    }

    public void start() {
        logger.info("Starting Stats collection with: {}", VirtualThreads.getThreadingMode());
        
        if (VirtualThreads.isEnabled()) {
            // Use virtual threads for stats processing - can handle much higher throughput
            threadPool = VirtualThreads.createExecutorService("locust4j-stats#");
            logger.info("Virtual thread executor created for stats processing (unlimited capacity)");
        } else {
            // Fallback to platform threads
            threadPool = new ThreadPoolExecutor(2, Integer.MAX_VALUE, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName(String.format("locust4j-stats#%d#", threadNumber.getAndIncrement()));
                    return thread;
                }
            });
            logger.info("Platform thread pool created for stats processing (initial: 2 threads)");
        }

        threadPool.submit(new StatsTimer(this));
        threadPool.submit(this);
        logger.debug("Started StatsTimer and Stats processing threads");
    }

    public void stop() {
        threadPool.shutdownNow();
    }

    public Queue<RequestSuccess> getReportSuccessQueue() {
        return this.reportSuccessQueue;
    }

    public Queue<RequestFailure> getReportFailureQueue() {
        return this.reportFailureQueue;
    }

    public Queue<Boolean> getClearStatsQueue() {
        return this.clearStatsQueue;
    }

    public BlockingQueue<Map<String, Object>> getMessageToRunnerQueue() {
        return this.messageToRunnerQueue;
    }

    public void wakeMeUp() {
        lock.lock();
        try {
            condition.signalAll();
            if (VirtualThreads.isVerbose()) {
                logger.trace("Stats thread woken up for processing");
            }
        } finally {
            lock.unlock();
        }
    }

    private void sleep() {
        lock.lock();
        try {
            condition.await();
        } catch (InterruptedException ex) {
            logger.debug("Stats thread interrupted while sleeping", ex);
            Thread.currentThread().interrupt(); // Restore interrupted status
        } catch (Exception ex) {
            logger.error("Error while Stats thread was waiting", ex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * User code reports successful and failed records to Stats.
     * If the sending speed is too fast, single-threaded stats may be a bottleneck and reports wrong RPS.
     */
    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        Thread.currentThread().setName(name + "stats");

        while (true) {

            boolean allEmpty = true;

            RequestSuccess successMessage = reportSuccessQueue.poll();
            if (successMessage != null) {
                this.logRequest(successMessage.getRequestType(), successMessage.getName(),
                    successMessage.getResponseTime()
                    , successMessage.getContentLength());
                allEmpty = false;
            }

            RequestFailure failureMessage = reportFailureQueue.poll();
            if (null != failureMessage) {
                this.logRequest(failureMessage.getRequestType(), failureMessage.getName(), failureMessage.getResponseTime(), 0);
                this.logError(failureMessage.getRequestType(), failureMessage.getName(), failureMessage.getError());
                allEmpty = false;
            }

            Boolean needToClearStats = clearStatsQueue.poll();
            if (null != needToClearStats && needToClearStats) {
                this.clearAll();
                allEmpty = false;
            }

            Boolean timeToReport = timeToReportQueue.poll();
            if (null != timeToReport) {
                Map<String, Object> data = this.collectReportData();
                messageToRunnerQueue.add(data);
                allEmpty = false;
            }

            if (allEmpty) {
                // if all the queues are empty, stats will sleep to avoid endless loop.
                this.sleep();
            }
        }
    }

    protected StatsEntry getTotal() {
        return this.total;
    }

    protected StatsEntry get(String name, String method) {
        StatsEntry entry = this.entries.get(name + method);
        if (null == entry) {
            entry = new StatsEntry(name, method);
            entry.reset();
            this.entries.put(name + method, entry);
        }
        return entry;
    }

    public void logRequest(String method, String name, long responseTime, long contentLength) {
        this.total.log(responseTime, contentLength);
        this.get(name, method).log(responseTime, contentLength);
    }

    public void logError(String method, String name, String error) {
        this.total.logError(error);
        this.get(name, method).logError(error);

        String key = Utils.md5(method, name, error);
        if (null == key) {
            key = method + name + error;
        }
        StatsError entry = this.errors.get(key);
        if (null == entry) {
            entry = new StatsError(name, method, error);
            this.errors.put(key, entry);
        }
        entry.occured();
    }

    public void clearAll() {
        this.total = new StatsEntry("Total");
        this.total.reset();
        this.entries = new HashMap<>(8);
        this.errors = new HashMap<>(8);
    }

    protected List<Map<String, Object>> serializeStats() {
        List<Map<String, Object>> entries = new ArrayList<>(this.entries.size());
        for (Map.Entry<String, StatsEntry> item : this.entries.entrySet()) {
            StatsEntry entry = item.getValue();
            if (!(entry.getNumRequests() == 0 && entry.getNumFailures() == 0)) {
                entries.add(entry.getStrippedReport());
            }
        }
        return entries;
    }

    public Map<String, Map<String, Object>> serializeErrors() {
        Map<String, Map<String, Object>> errors = new HashMap<>(8);
        for (Map.Entry<String, StatsError> item : this.errors.entrySet()) {
            String key = item.getKey();
            StatsError error = item.getValue();
            errors.put(key, error.toMap());
        }
        return errors;
    }

    protected Map<String, Object> collectReportData() {
        Map<String, Object> data = new HashMap<>(3);

        data.put("stats", this.serializeStats());
        data.put("stats_total", this.total.getStrippedReport());
        data.put("errors", this.serializeErrors());

        errors.clear();

        return data;
    }

    private static class StatsInstanceHolder {
        private static final Stats INSTANCE = new Stats();
    }

    private static class StatsTimer implements Runnable {
        private static final int SLAVE_REPORT_INTERVAL = 3000;
        protected Stats stats;

        private StatsTimer(Stats stats) {
            this.stats = stats;
        }

        @Override
        public void run() {
            String name = Thread.currentThread().getName();
            Thread.currentThread().setName(name + "stats-timer");

            while (true) {
                try {
                    Thread.sleep(SLAVE_REPORT_INTERVAL);
                } catch (InterruptedException ex) {
                    return;
                } catch (Exception ex) {
                    logger.error(ex.getMessage());
                }
                stats.timeToReportQueue.offer(true);
                stats.wakeMeUp();
            }
        }
    }

}