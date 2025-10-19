package task;

import com.github.myzhan.locust4j.Locust;

/**
 * Example demonstrating Virtual Threads usage in Locust4j.
 * 
 * This example shows how to enable and use virtual threads for high-scale load testing.
 * No code changes required - just set system properties!
 * 
 * To run with virtual threads:
 * java -Dlocust4j.virtualThreads.enabled=true -cp locust4j.jar task.VirtualThreadsExample
 * 
 * To run with verbose logging:
 * java -Dlocust4j.virtualThreads.enabled=true \
 *      -Dlocust4j.virtualThreads.verbose=true \
 *      -cp locust4j.jar task.VirtualThreadsExample
 *
 * @author locust4j
 */
public class VirtualThreadsExample {

    public static void main(String[] args) {
        
        // Example 1: Basic usage - no code changes needed!
        // Just enable virtual threads via system property
        basicExample();
        
        // Example 2: High-scale load testing
        // highScaleExample();
        
        // Example 3: Checking virtual threads status programmatically
        // checkVirtualThreadsStatus();
    }
    
    /**
     * Basic example - works identically with or without virtual threads.
     * Enable virtual threads by setting: -Dlocust4j.virtualThreads.enabled=true
     */
    private static void basicExample() {
        System.out.println("=== Basic Virtual Threads Example ===\n");
        
        Locust locust = Locust.getInstance();
        locust.setMasterHost("127.0.0.1");
        locust.setMasterPort(5557);
        
        // With virtual threads, you can handle much higher RPS
        locust.setMaxRPS(100000);
        
        // Your existing tasks work without any changes!
        locust.run(new TaskAlwaysSuccess(), new TaskAlwaysFail());
        
        System.out.println("\nLoad test started. Check logs for virtual threads status.");
    }
    
    /**
     * High-scale example - demonstrates massive concurrency with virtual threads.
     * This would be impossible with platform threads due to memory constraints.
     */
    private static void highScaleExample() {
        System.out.println("=== High-Scale Virtual Threads Example ===\n");
        System.out.println("This example demonstrates 100K+ concurrent users");
        System.out.println("Only possible with virtual threads enabled!\n");
        
        Locust locust = Locust.getInstance();
        locust.setMasterHost("127.0.0.1");
        locust.setMasterPort(5557);
        
        // Virtual threads enable massive scale
        // Platform threads: Max ~8,000 users (16-32GB memory)
        // Virtual threads: 100,000+ users (< 2GB memory)
        locust.setMaxRPS(500000); // Half a million requests per second!
        
        locust.run(new TaskAlwaysSuccess());
        
        System.out.println("\nHigh-scale load test started.");
        System.out.println("Monitor memory usage - should be < 2GB for 100K users");
    }
    
    /**
     * Example showing how to check virtual threads status programmatically.
     * Useful for conditional logic or monitoring.
     */
    private static void checkVirtualThreadsStatus() {
        System.out.println("=== Virtual Threads Status Check ===\n");
        
        // Check if virtual threads are enabled
        String enabled = System.getProperty("locust4j.virtualThreads.enabled", "false");
        String verbose = System.getProperty("locust4j.virtualThreads.verbose", "false");
        
        System.out.println("Virtual Threads Enabled: " + enabled);
        System.out.println("Verbose Logging Enabled: " + verbose);
        System.out.println("Java Version: " + System.getProperty("java.version"));
        
        // Virtual threads are only available on Java 21+
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getMajorVersion(javaVersion);
        
        System.out.println("\nVirtual Threads Support:");
        if (majorVersion >= 21) {
            System.out.println("✓ Java 21+ detected - Virtual threads available");
            if ("true".equals(enabled)) {
                System.out.println("✓ Virtual threads are ENABLED");
                System.out.println("  → Worker pool: Virtual threads");
                System.out.println("  → Stats processing: Virtual threads");
                System.out.println("  → RPC communication: Virtual threads");
                System.out.println("  → Rate limiters: Virtual threads");
            } else {
                System.out.println("! Virtual threads are DISABLED");
                System.out.println("  → Using platform threads");
                System.out.println("  → Set -Dlocust4j.virtualThreads.enabled=true to enable");
            }
        } else {
            System.out.println("✗ Java " + majorVersion + " detected - Virtual threads not available");
            System.out.println("  → Upgrade to Java 21+ for virtual threads support");
            System.out.println("  → Currently using platform threads");
        }
        
        System.out.println("\nMemory Impact:");
        if (majorVersion >= 21 && "true".equals(enabled)) {
            System.out.println("  10,000 users:   ~10-20 MB  (vs 10-20 GB platform threads)");
            System.out.println("  100,000 users:  ~100-200 MB (impossible with platform threads)");
            System.out.println("  1,000,000 users: ~1-2 GB    (impossible with platform threads)");
        } else {
            System.out.println("  10,000 users:   ~10-20 GB  (platform threads)");
            System.out.println("  100,000 users:  NOT POSSIBLE (insufficient memory)");
        }
        
        System.out.println();
    }
    
    /**
     * Extract major version from Java version string.
     */
    private static int getMajorVersion(String version) {
        try {
            // Handle versions like "21.0.1", "1.8.0_292", "17.0.2"
            if (version.startsWith("1.")) {
                // Old format: 1.8.0_292 -> 8
                return Integer.parseInt(version.substring(2, 3));
            } else {
                // New format: 21.0.1 -> 21
                int dotIndex = version.indexOf('.');
                if (dotIndex > 0) {
                    return Integer.parseInt(version.substring(0, dotIndex));
                }
                return Integer.parseInt(version);
            }
        } catch (Exception e) {
            return 8; // Default to 8 if parsing fails
        }
    }
    
    /**
     * Print memory statistics - useful for comparing platform vs virtual threads.
     */
    private static void printMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        System.out.println("\n=== Memory Statistics ===");
        System.out.println("Max Memory:   " + (maxMemory / 1024 / 1024) + " MB");
        System.out.println("Total Memory: " + (totalMemory / 1024 / 1024) + " MB");
        System.out.println("Used Memory:  " + (usedMemory / 1024 / 1024) + " MB");
        System.out.println("Free Memory:  " + (freeMemory / 1024 / 1024) + " MB");
        System.out.println();
    }
}
