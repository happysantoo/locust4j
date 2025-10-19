package examples.advanced;

import com.github.myzhan.locust4j.AbstractTask;
import com.github.myzhan.locust4j.Locust;
import com.github.myzhan.locust4j.ratelimit.RampUpRateLimiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-world example simulating an e-commerce load test scenario.
 * 
 * This example demonstrates:
 * - Multiple task types with different weights
 * - Realistic user behavior patterns
 * - Ramp-up load testing
 * - Session-based state management
 * - Comprehensive metrics recording
 * 
 * Usage:
 *   java examples.advanced.EcommerceLoadTest <master-host> <master-port>
 */
public class EcommerceLoadTest {

    private static final AtomicInteger sessionCounter = new AtomicInteger(0);
    private static final AtomicLong totalOrders = new AtomicLong(0);
    private static final AtomicLong totalRevenue = new AtomicLong(0);

    /**
     * Simulates browsing product listings.
     */
    private static class BrowseProductsTask extends AbstractTask {
        private final String sessionId;

        public BrowseProductsTask(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public int getWeight() {
            return 50; // 50% of requests
        }

        @Override
        public String getName() {
            return "browse-products";
        }

        @Override
        public void execute() throws Exception {
            long start = System.currentTimeMillis();
            
            try {
                // Simulate API call
                Thread.sleep(30 + (long)(Math.random() * 70)); // 30-100ms
                
                long elapsed = System.currentTimeMillis() - start;
                Locust.getInstance().recordSuccess("http", "GET /products", 
                    elapsed, 4096);
                    
                if (Math.random() < 0.1) { // Log 10% of requests
                    System.out.println(String.format("[%s] Browsed products (%.0fms)", 
                        sessionId, (double)elapsed));
                }
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                Locust.getInstance().recordFailure("http", "GET /products", 
                    elapsed, e.getMessage());
            }
        }
    }

    /**
     * Simulates viewing product details.
     */
    private static class ViewProductTask extends AbstractTask {
        private final String sessionId;

        public ViewProductTask(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public int getWeight() {
            return 30; // 30% of requests
        }

        @Override
        public String getName() {
            return "view-product";
        }

        @Override
        public void execute() throws Exception {
            long start = System.currentTimeMillis();
            int productId = 1000 + (int)(Math.random() * 5000);
            
            try {
                // Simulate API call
                Thread.sleep(40 + (long)(Math.random() * 80)); // 40-120ms
                
                long elapsed = System.currentTimeMillis() - start;
                Locust.getInstance().recordSuccess("http", 
                    "GET /products/" + productId, elapsed, 2048);
                    
                if (Math.random() < 0.1) {
                    System.out.println(String.format("[%s] Viewed product #%d (%.0fms)", 
                        sessionId, productId, (double)elapsed));
                }
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                Locust.getInstance().recordFailure("http", 
                    "GET /products/" + productId, elapsed, e.getMessage());
            }
        }
    }

    /**
     * Simulates adding items to cart.
     */
    private static class AddToCartTask extends AbstractTask {
        private final String sessionId;

        public AddToCartTask(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public int getWeight() {
            return 15; // 15% of requests
        }

        @Override
        public String getName() {
            return "add-to-cart";
        }

        @Override
        public void execute() throws Exception {
            long start = System.currentTimeMillis();
            
            try {
                // Simulate API call
                Thread.sleep(50 + (long)(Math.random() * 100)); // 50-150ms
                
                // Simulate occasional cart conflicts
                if (Math.random() < 0.02) {
                    throw new Exception("Cart conflict - item out of stock");
                }
                
                long elapsed = System.currentTimeMillis() - start;
                Locust.getInstance().recordSuccess("http", "POST /cart/items", 
                    elapsed, 512);
                    
                System.out.println(String.format("[%s] Added item to cart (%.0fms)", 
                    sessionId, (double)elapsed));
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                Locust.getInstance().recordFailure("http", "POST /cart/items", 
                    elapsed, e.getMessage());
                System.err.println(String.format("[%s] Cart error: %s", 
                    sessionId, e.getMessage()));
            }
        }
    }

    /**
     * Simulates checkout process.
     */
    private static class CheckoutTask extends AbstractTask {
        private final String sessionId;

        public CheckoutTask(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public int getWeight() {
            return 5; // 5% of requests
        }

        @Override
        public String getName() {
            return "checkout";
        }

        @Override
        public void execute() throws Exception {
            long start = System.currentTimeMillis();
            
            try {
                // Simulate payment processing
                Thread.sleep(200 + (long)(Math.random() * 300)); // 200-500ms
                
                // Simulate occasional payment failures
                if (Math.random() < 0.03) {
                    throw new Exception("Payment declined");
                }
                
                long elapsed = System.currentTimeMillis() - start;
                int orderAmount = 50 + (int)(Math.random() * 200); // $50-$250
                
                Locust.getInstance().recordSuccess("http", "POST /checkout", 
                    elapsed, 1024);
                
                totalOrders.incrementAndGet();
                totalRevenue.addAndGet(orderAmount);
                
                System.out.println(String.format("[%s] ✓ Order completed: $%d (%.0fms)", 
                    sessionId, orderAmount, (double)elapsed));
                    
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                Locust.getInstance().recordFailure("http", "POST /checkout", 
                    elapsed, e.getMessage());
                System.err.println(String.format("[%s] ✗ Checkout failed: %s", 
                    sessionId, e.getMessage()));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: EcommerceLoadTest <master-host> <master-port>");
            System.out.println("Example: EcommerceLoadTest 127.0.0.1 5557");
            System.exit(1);
        }

        String masterHost = args[0];
        int masterPort = Integer.parseInt(args[1]);

        System.out.println("E-commerce Load Test Scenario");
        System.out.println("==============================");
        System.out.println("Simulating realistic e-commerce user behavior:");
        System.out.println("  50% - Browse products");
        System.out.println("  30% - View product details");
        System.out.println("  15% - Add to cart");
        System.out.println("   5% - Checkout");
        System.out.println();

        // Create ramp-up rate limiter: start at 0, ramp up to 200 RPS over 60 seconds
        RampUpRateLimiter rateLimiter = new RampUpRateLimiter(
            200,                    // maxThreshold: 200 RPS
            10,                     // rampUpStep: increase by 10 RPS
            3,                      // rampUpPeriod: every 3 seconds
            TimeUnit.SECONDS,       // rampUpTimeUnit
            100,                    // refillPeriod: 100ms
            TimeUnit.MILLISECONDS   // refillUnit
        );

        System.out.println("Rate limiting: Ramping up to 200 RPS over 60 seconds");
        System.out.println(String.format("Connecting to Locust master at %s:%d", 
            masterHost, masterPort));
        System.out.println();

        // Configure Locust
        Locust locust = Locust.getInstance();
        locust.setMasterHost(masterHost);
        locust.setMasterPort(masterPort);
        locust.setMaxRPS(200);
        locust.setRateLimiter(rateLimiter);

        // Create session ID for this worker
        String sessionId = "session-" + sessionCounter.incrementAndGet();

        // Create tasks
        AbstractTask[] tasks = {
            new BrowseProductsTask(sessionId),
            new ViewProductTask(sessionId),
            new AddToCartTask(sessionId),
            new CheckoutTask(sessionId)
        };

        // Start statistics reporter
        startStatisticsReporter();

        System.out.println("Starting e-commerce load test...");
        System.out.println("Press Ctrl+C to stop");
        System.out.println();

        locust.run(tasks);
    }

    private static void startStatisticsReporter() {
        Thread reporter = new Thread(() -> {
            try {
                Thread.sleep(10000); // Wait 10 seconds before first report
                
                while (!Thread.currentThread().isInterrupted()) {
                    long orders = totalOrders.get();
                    long revenue = totalRevenue.get();
                    
                    System.out.println(String.format(
                        "\n=== Statistics: %d orders | $%d total revenue | $%.2f avg ===\n",
                        orders, revenue, orders > 0 ? (double)revenue / orders : 0.0
                    ));
                    
                    Thread.sleep(30000); // Report every 30 seconds
                }
            } catch (InterruptedException e) {
                // Exit gracefully
            }
        });
        reporter.setDaemon(true);
        reporter.start();
    }
}
