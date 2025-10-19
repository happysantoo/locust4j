package com.github.myzhan.locust4j;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for AbstractTask class and error handling.
 */
public class TestAbstractTask {

    private static class SimpleTask extends AbstractTask {
        private final String name;
        private final int weight;
        private final AtomicInteger execCount = new AtomicInteger(0);

        public SimpleTask(String name, int weight) {
            this.name = name;
            this.weight = weight;
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
            execCount.incrementAndGet();
        }

        public int getExecutionCount() {
            return execCount.get();
        }
    }

    private static class FailingTask extends AbstractTask {
        private final RuntimeException exception;

        public FailingTask(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public int getWeight() {
            return 1;
        }

        @Override
        public String getName() {
            return "failing-task";
        }

        @Override
        public void execute() throws Exception {
            throw exception;
        }
    }

    @Test
    public void testTaskBasicProperties() {
        SimpleTask task = new SimpleTask("test-task", 5);
        assertEquals("Task name should match", "test-task", task.getName());
        assertEquals("Task weight should match", 5, task.getWeight());
    }

    @Test
    public void testTaskExecution() throws Exception {
        SimpleTask task = new SimpleTask("exec-task", 1);
        assertEquals("Execution count should be 0", 0, task.getExecutionCount());
        
        task.execute();
        assertEquals("Execution count should be 1", 1, task.getExecutionCount());
        
        task.execute();
        assertEquals("Execution count should be 2", 2, task.getExecutionCount());
    }

    @Test
    public void testZeroWeight() {
        SimpleTask task = new SimpleTask("zero-weight", 0);
        assertEquals("Should support zero weight", 0, task.getWeight());
    }

    @Test
    public void testNegativeWeight() {
        SimpleTask task = new SimpleTask("negative-weight", -1);
        assertEquals("Should support negative weight", -1, task.getWeight());
    }

    @Test
    public void testVeryHighWeight() {
        SimpleTask task = new SimpleTask("high-weight", Integer.MAX_VALUE);
        assertEquals("Should support max weight", Integer.MAX_VALUE, task.getWeight());
    }

    @Test(expected = NullPointerException.class)
    public void testFailingTaskWithNullPointerException() throws Exception {
        FailingTask task = new FailingTask(new NullPointerException("Test NPE"));
        task.execute();
    }

    @Test(expected = IllegalStateException.class)
    public void testFailingTaskWithIllegalStateException() throws Exception {
        FailingTask task = new FailingTask(new IllegalStateException("Test ISE"));
        task.execute();
    }

    @Test(expected = RuntimeException.class)
    public void testFailingTaskWithGenericRuntimeException() throws Exception {
        FailingTask task = new FailingTask(new RuntimeException("Test exception"));
        task.execute();
    }

    @Test
    public void testTaskWithEmptyName() {
        SimpleTask task = new SimpleTask("", 1);
        assertEquals("Should support empty name", "", task.getName());
    }

    @Test
    public void testTaskWithLongName() {
        String longName = "a".repeat(1000);
        SimpleTask task = new SimpleTask(longName, 1);
        assertEquals("Should support long name", longName, task.getName());
    }

    @Test
    public void testTaskWithSpecialCharactersInName() {
        String specialName = "test-task_#$%^&*()[]{}";
        SimpleTask task = new SimpleTask(specialName, 1);
        assertEquals("Should support special characters", specialName, task.getName());
    }

    @Test
    public void testMultipleTasks() {
        SimpleTask task1 = new SimpleTask("task1", 1);
        SimpleTask task2 = new SimpleTask("task2", 2);
        SimpleTask task3 = new SimpleTask("task3", 3);

        assertNotEquals("Tasks should be different", task1, task2);
        assertEquals("Weight should match", 2, task2.getWeight());
        assertEquals("Weight should match", 3, task3.getWeight());
    }

    @Test
    public void testConcurrentExecution() throws InterruptedException {
        final SimpleTask task = new SimpleTask("concurrent-task", 1);
        final int threadCount = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    task.execute();
                } catch (Exception e) {
                    // Ignore
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals("All executions should be counted", threadCount, task.getExecutionCount());
    }
}
