package com.github.myzhan.locust4j.integration;

import com.github.myzhan.locust4j.AbstractTask;
import com.github.myzhan.locust4j.Locust;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Integration test that dynamically installs Locust and tests end-to-end communication.
 * 
 * This test verifies:
 * - Python/pip availability
 * - Dynamic Locust installation
 * - Locust master startup
 * - Java client connection to master
 * - Task execution and stats reporting
 * - Graceful shutdown
 */
public class LocustIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(LocustIntegrationTest.class);
    private static final int TEST_PORT = 25557;
    private static final String PYTHON_VENV_DIR = "target/locust-venv";
    private static final int TEST_EXECUTION_TIMEOUT_SECONDS = 30;
    
    private Process locustMasterProcess;
    private String pythonExecutable;
    private boolean locustInstalled = false;
    private AtomicBoolean testPassed = new AtomicBoolean(false);

    /**
     * Simple test task that tracks execution count.
     */
    private static class SimpleTestTask extends AbstractTask {
        private final AtomicInteger executionCount = new AtomicInteger(0);
        private final CountDownLatch executionLatch;
        private final int weight;
        private final String name;

        public SimpleTestTask(String name, int weight, CountDownLatch latch) {
            this.name = name;
            this.weight = weight;
            this.executionLatch = latch;
        }

        @Override
        public int getWeight() {
            return weight;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void execute() throws Exception {
            int count = executionCount.incrementAndGet();
            logger.debug("Task {} executed {} times", name, count);
            
            // Simulate work
            Thread.sleep(10);
            
            // Signal completion
            if (executionLatch != null) {
                executionLatch.countDown();
            }
        }

        public int getExecutionCount() {
            return executionCount.get();
        }
    }

    @Before
    public void setUp() throws Exception {
        logger.info("Setting up integration test environment...");
        
        // Check if we should skip integration tests
        String skipIntegration = System.getProperty("skipIntegrationTests");
        if ("true".equalsIgnoreCase(skipIntegration)) {
            logger.info("Skipping integration tests (skipIntegrationTests=true)");
            return;
        }

        // Setup Python virtual environment and install Locust
        setupPythonEnvironment();
        
        if (!locustInstalled) {
            logger.warn("Locust installation failed, skipping integration test");
            return;
        }

        // Start Locust master
        startLocustMaster();
    }

    @After
    public void tearDown() {
        logger.info("Tearing down integration test environment...");
        
        // Stop Locust master
        stopLocustMaster();
        
        // Log test results
        if (testPassed.get()) {
            logger.info("Integration test completed successfully");
        }
    }

    /**
     * Sets up Python virtual environment and installs Locust.
     */
    private void setupPythonEnvironment() throws Exception {
        logger.info("Setting up Python environment and installing Locust...");
        
        // Check if Python 3 is available
        String pythonCmd = findPython();
        if (pythonCmd == null) {
            logger.warn("Python 3 not found. Install Python 3 to run integration tests.");
            return;
        }
        
        logger.info("Found Python: {}", pythonCmd);
        
        // Create virtual environment if it doesn't exist
        Path venvPath = Paths.get(PYTHON_VENV_DIR);
        if (!Files.exists(venvPath)) {
            logger.info("Creating Python virtual environment at {}", PYTHON_VENV_DIR);
            ProcessBuilder pb = new ProcessBuilder(pythonCmd, "-m", "venv", PYTHON_VENV_DIR);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                logger.error("Failed to create virtual environment");
                return;
            }
            logger.info("Virtual environment created successfully");
        }
        
        // Determine Python executable in venv
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            pythonExecutable = PYTHON_VENV_DIR + "/Scripts/python.exe";
        } else {
            pythonExecutable = PYTHON_VENV_DIR + "/bin/python";
        }
        
        // Check if Locust is already installed
        if (isLocustInstalled()) {
            logger.info("Locust is already installed");
            locustInstalled = true;
            return;
        }
        
        // Install Locust
        logger.info("Installing Locust...");
        ProcessBuilder pb = new ProcessBuilder(
            pythonExecutable, "-m", "pip", "install", "--quiet", "locust"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // Log output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("pip: {}", line);
            }
        }
        
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            logger.error("Locust installation timed out");
            process.destroyForcibly();
            return;
        }
        
        if (process.exitValue() != 0) {
            logger.error("Failed to install Locust (exit code: {})", process.exitValue());
            return;
        }
        
        locustInstalled = true;
        logger.info("Locust installed successfully");
    }

    /**
     * Finds Python 3 executable on the system.
     */
    private String findPython() {
        String[] candidates = {"python3", "python"};
        
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String version = reader.readLine();
                    if (version != null && version.contains("Python 3")) {
                        return cmd;
                    }
                }
                
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Try next candidate
            }
        }
        
        return null;
    }

    /**
     * Checks if Locust is installed in the virtual environment.
     */
    private boolean isLocustInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "locust", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Starts Locust master process.
     */
    private void startLocustMaster() throws Exception {
        logger.info("Starting Locust master on port {}...", TEST_PORT);
        
        // Create a minimal locustfile
        Path locustfile = Paths.get("target/test_locustfile.py");
        String locustfileContent = 
            "from locust import User, task, between\n" +
            "\n" +
            "class TestUser(User):\n" +
            "    wait_time = between(1, 2)\n" +
            "    \n" +
            "    @task\n" +
            "    def test_task(self):\n" +
            "        pass\n";
        Files.write(locustfile, locustfileContent.getBytes());
        
        // Start Locust master with auto-spawn
        ProcessBuilder pb = new ProcessBuilder(
            pythonExecutable, "-m", "locust",
            "--master",
            "--master-bind-port", String.valueOf(TEST_PORT),
            "--expect-workers", "1",
            "-f", locustfile.toString(),
            "--headless",
            "--users", "5",
            "--spawn-rate", "5"
        );
        
        pb.redirectErrorStream(true);
        locustMasterProcess = pb.start();
        
        // Monitor output in background
        Thread outputMonitor = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(locustMasterProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("Locust master: {}", line);
                }
            } catch (IOException e) {
                // Ignore - process stopped
            }
        });
        outputMonitor.setDaemon(true);
        outputMonitor.start();
        
        // Wait for master to be ready
        logger.info("Waiting for Locust master to start...");
        Thread.sleep(3000); // Give it time to bind port
        
        logger.info("Locust master started");
    }

    /**
     * Stops Locust master process.
     */
    private void stopLocustMaster() {
        if (locustMasterProcess != null && locustMasterProcess.isAlive()) {
            logger.info("Stopping Locust master...");
            locustMasterProcess.destroy();
            
            try {
                if (!locustMasterProcess.waitFor(10, TimeUnit.SECONDS)) {
                    logger.warn("Force killing Locust master");
                    locustMasterProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    public void testEndToEndIntegration() throws Exception {
        // Skip if Locust not installed
        if (!locustInstalled) {
            logger.warn("Skipping integration test - Locust not installed");
            return;
        }

        logger.info("Running end-to-end integration test...");
        
        // Create test tasks - increase latch count to ensure both tasks execute
        // With weight 2:1, we need enough executions for statistical distribution
        CountDownLatch executionLatch = new CountDownLatch(30);
        SimpleTestTask task1 = new SimpleTestTask("integration_test_task_1", 2, executionLatch);
        SimpleTestTask task2 = new SimpleTestTask("integration_test_task_2", 1, executionLatch);
        
        // Configure Locust
        Locust locust = Locust.getInstance();
        locust.setMasterHost("127.0.0.1");
        locust.setMasterPort(TEST_PORT);
        locust.setMaxRPS(10);
        
        // Run test with timeout
        try {
            // Start client in background
            Thread clientThread = new Thread(() -> {
                try {
                    locust.run(task1, task2);
                } catch (Exception e) {
                    logger.error("Client execution failed", e);
                }
            });
            clientThread.setDaemon(true);
            clientThread.start();
            
            // Wait for some executions
            boolean executed = executionLatch.await(TEST_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // Stop the client
            locust.stop();
            clientThread.join(5000);
            
            // Verify results
            assertTrue("Tasks should have executed", executed);
            
            int task1Count = task1.getExecutionCount();
            int task2Count = task2.getExecutionCount();
            
            logger.info("Task 1 executed {} times", task1Count);
            logger.info("Task 2 executed {} times", task2Count);
            
            assertTrue("Task 1 should have executed", task1Count > 0);
            assertTrue("Task 2 should have executed", task2Count > 0);
            
            // Verify weight distribution (task1 should execute roughly 2x task2)
            double ratio = (double) task1Count / task2Count;
            logger.info("Task execution ratio: {}", ratio);
            assertTrue("Task weight should be respected (ratio should be between 1.5 and 3.0)", 
                ratio >= 1.5 && ratio <= 3.0);
            
            testPassed.set(true);
            logger.info("Integration test passed!");
            
        } catch (Exception e) {
            logger.error("Integration test failed", e);
            throw e;
        }
    }

    @Test
    public void testVirtualThreadsIntegration() throws Exception {
        // Skip if Locust not installed
        if (!locustInstalled) {
            logger.warn("Skipping virtual threads integration test - Locust not installed");
            return;
        }

        // Enable virtual threads
        System.setProperty("locust4j.virtualThreads.enabled", "true");
        
        try {
            logger.info("Running virtual threads integration test...");
            
            // Create test task
            CountDownLatch executionLatch = new CountDownLatch(5);
            SimpleTestTask task = new SimpleTestTask("vthread_test_task", 1, executionLatch);
            
            // Configure Locust
            Locust locust = Locust.getInstance();
            locust.setMasterHost("127.0.0.1");
            locust.setMasterPort(TEST_PORT);
            locust.setMaxRPS(5);
            
            // Start client
            Thread clientThread = new Thread(() -> {
                try {
                    locust.run(task);
                } catch (Exception e) {
                    logger.error("Virtual threads client execution failed", e);
                }
            });
            clientThread.setDaemon(true);
            clientThread.start();
            
            // Wait for executions
            boolean executed = executionLatch.await(TEST_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // Stop the client
            locust.stop();
            clientThread.join(5000);
            
            // Verify
            assertTrue("Tasks should have executed with virtual threads", executed);
            assertTrue("Task should have executed at least 5 times", task.getExecutionCount() >= 5);
            
            logger.info("Virtual threads integration test passed!");
            
        } finally {
            System.clearProperty("locust4j.virtualThreads.enabled");
        }
    }
}
