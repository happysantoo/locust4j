package examples.ratelimit;

import com.github.myzhan.locust4j.AbstractTask;
import com.github.myzhan.locust4j.Locust;
import com.github.myzhan.locust4j.ratelimit.StableRateLimiter;

/**
 * Example demonstrating stable rate limiting for consistent load generation.
 * 
 * This example shows how to:
 * - Create and configure a stable rate limiter
 * - Apply rate limiting to control request throughput
 * - Monitor and adjust rate limits
 * 
 * Usage:
 *   java examples.ratelimit.StableRpsExample <master-host> <master-port>
 */
public class StableRpsExample {

    private static class HttpGetTask extends AbstractTask {
        private int sequenceNumber = 0;

        @Override
        public int getWeight() {
            return 1;
        }

        @Override
        public String getName() {
            return "http-get";
        }

        @Override
        public void execute() throws Exception {
            sequenceNumber++;
            long start = System.currentTimeMillis();
            
            try {
                // Simulate HTTP GET request
                simulateHttpRequest();
                
                long elapsed = System.currentTimeMillis() - start;
                
                // Record success
                Locust.getInstance().recordSuccess("http", "GET /api/users", 
                    elapsed, 1024);
                    
                System.out.println(String.format("[%d] Success: GET /api/users (%.0fms)", 
                    sequenceNumber, (double)elapsed));
                    
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                
                // Record failure
                Locust.getInstance().recordFailure("http", "GET /api/users", 
                    elapsed, e.getMessage());
                    
                System.err.println(String.format("[%d] Failure: %s", 
                    sequenceNumber, e.getMessage()));
            }
        }

        private void simulateHttpRequest() throws Exception {
            // Simulate network latency (50-150ms)
            Thread.sleep(50 + (long)(Math.random() * 100));
            
            // Simulate occasional failures (5% failure rate)
            if (Math.random() < 0.05) {
                throw new Exception("Connection timeout");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: StableRpsExample <master-host> <master-port>");
            System.out.println("Example: StableRpsExample 127.0.0.1 5557");
            System.exit(1);
        }

        String masterHost = args[0];
        int masterPort = Integer.parseInt(args[1]);

        // Create a stable rate limiter: 100 requests per second
        int targetRps = 100;
        StableRateLimiter rateLimiter = new StableRateLimiter(targetRps);
        
        System.out.println(String.format("Configuring stable rate limiter: %d RPS", targetRps));
        System.out.println(String.format("Connecting to Locust master at %s:%d", 
            masterHost, masterPort));

        // Configure Locust
        Locust locust = Locust.getInstance();
        locust.setMasterHost(masterHost);
        locust.setMasterPort(masterPort);
        locust.setMaxRPS(targetRps);
        
        // Set the rate limiter
        locust.setRateLimiter(rateLimiter);

        // Create and run task
        HttpGetTask task = new HttpGetTask();
        
        System.out.println("Starting load test with stable rate limiting...");
        System.out.println("Press Ctrl+C to stop");
        
        locust.run(task);
    }
}
