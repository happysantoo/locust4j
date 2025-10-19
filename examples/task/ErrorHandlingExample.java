package examples.task;

import com.github.myzhan.locust4j.AbstractTask;
import com.github.myzhan.locust4j.Locust;

/**
 * Example demonstrating comprehensive error handling in tasks.
 * 
 * This example shows how to:
 * - Handle different types of exceptions
 * - Record failures with detailed messages
 * - Implement retry logic
 * - Log error details for debugging
 * 
 * Usage:
 *   java examples.task.ErrorHandlingExample <master-host> <master-port>
 */
public class ErrorHandlingExample {

    /**
     * Task that demonstrates proper error handling.
     */
    private static class ResilientTask extends AbstractTask {
        private static final int MAX_RETRIES = 3;
        private int attemptNumber = 0;

        @Override
        public int getWeight() {
            return 1;
        }

        @Override
        public String getName() {
            return "resilient-task";
        }

        @Override
        public void execute() throws Exception {
            attemptNumber++;
            long startTime = System.currentTimeMillis();
            
            String operation = "API-Call-" + attemptNumber;
            
            try {
                // Try to execute with retries
                executeWithRetry(operation, MAX_RETRIES);
                
                long elapsed = System.currentTimeMillis() - startTime;
                Locust.getInstance().recordSuccess("http", operation, elapsed, 512);
                
                System.out.println(String.format("✓ [%d] %s succeeded (%.0fms)", 
                    attemptNumber, operation, (double)elapsed));
                    
            } catch (TransientException e) {
                // Recoverable error - log and record failure
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = String.format("Transient error after %d retries: %s", 
                    MAX_RETRIES, e.getMessage());
                    
                Locust.getInstance().recordFailure("http", operation, elapsed, errorMsg);
                System.err.println(String.format("⚠ [%d] %s", attemptNumber, errorMsg));
                
            } catch (FatalException e) {
                // Non-recoverable error - log and record failure
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = String.format("Fatal error: %s", e.getMessage());
                
                Locust.getInstance().recordFailure("http", operation, elapsed, errorMsg);
                System.err.println(String.format("✗ [%d] %s", attemptNumber, errorMsg));
                
            } catch (Exception e) {
                // Unexpected error - log with full details
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = String.format("Unexpected error: %s", e.getClass().getName());
                
                Locust.getInstance().recordFailure("http", operation, elapsed, errorMsg);
                System.err.println(String.format("⚠ [%d] Unexpected: %s - %s", 
                    attemptNumber, e.getClass().getSimpleName(), e.getMessage()));
                e.printStackTrace();
            }
        }

        private void executeWithRetry(String operation, int maxRetries) throws Exception {
            int retries = 0;
            Exception lastException = null;
            
            while (retries <= maxRetries) {
                try {
                    simulateApiCall();
                    return; // Success
                    
                } catch (TransientException e) {
                    lastException = e;
                    retries++;
                    
                    if (retries <= maxRetries) {
                        System.out.println(String.format("  Retry %d/%d for %s: %s", 
                            retries, maxRetries, operation, e.getMessage()));
                        Thread.sleep(100 * retries); // Exponential backoff
                    }
                    
                } catch (FatalException e) {
                    // Don't retry fatal errors
                    throw e;
                }
            }
            
            // All retries exhausted
            throw lastException;
        }

        private void simulateApiCall() throws Exception {
            // Simulate network delay
            Thread.sleep(50 + (long)(Math.random() * 100));
            
            double rand = Math.random();
            
            if (rand < 0.05) {
                // 5% fatal errors (don't retry)
                throw new FatalException("Service unavailable (503)");
            } else if (rand < 0.15) {
                // 10% transient errors (retry)
                throw new TransientException("Connection timeout");
            }
            // 85% success
        }
    }

    /**
     * Exception representing transient/recoverable errors.
     */
    private static class TransientException extends Exception {
        public TransientException(String message) {
            super(message);
        }
    }

    /**
     * Exception representing fatal/non-recoverable errors.
     */
    private static class FatalException extends Exception {
        public FatalException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: ErrorHandlingExample <master-host> <master-port>");
            System.out.println("Example: ErrorHandlingExample 127.0.0.1 5557");
            System.exit(1);
        }

        String masterHost = args[0];
        int masterPort = Integer.parseInt(args[1]);

        System.out.println("Error Handling Example");
        System.out.println("======================");
        System.out.println("This example demonstrates:");
        System.out.println("  - Transient errors (retried automatically)");
        System.out.println("  - Fatal errors (not retried)");
        System.out.println("  - Proper error recording and logging");
        System.out.println();
        System.out.println(String.format("Connecting to Locust master at %s:%d", 
            masterHost, masterPort));

        // Configure Locust
        Locust locust = Locust.getInstance();
        locust.setMasterHost(masterHost);
        locust.setMasterPort(masterPort);

        // Create and run task
        ResilientTask task = new ResilientTask();
        
        System.out.println("Starting load test with error handling...");
        System.out.println("Press Ctrl+C to stop");
        System.out.println();
        
        locust.run(task);
    }
}
