# Locust4j Examples

This directory contains comprehensive examples demonstrating various features and usage patterns of locust4j.

## Quick Start

All examples require a running Locust master. Start one with:

```bash
locust --master --master-bind-host=127.0.0.1 --master-bind-port=5557
```

Then compile and run any example:

```bash
# Compile
mvn clean package

# Run an example
java -cp target/locust4j-2.2.5-jar-with-dependencies.jar examples.task.Main 127.0.0.1 5557
```

## Examples Overview

### Basic Tasks (`task/`)

#### `Main.java`
The simplest possible example showing basic task execution.
- Single task implementation
- Minimal configuration
- Good starting point for beginners

#### `TaskAlwaysSuccess.java`
Demonstrates a task that always succeeds.
- Success recording
- Response time tracking
- Basic metrics collection

#### `TaskAlwaysFail.java`
Demonstrates a task that always fails.
- Failure recording
- Error message handling
- Failure rate monitoring

#### `ErrorHandlingExample.java` ⭐ NEW
Comprehensive error handling patterns.
- Transient vs fatal error handling
- Automatic retry logic with exponential backoff
- Detailed error logging and categorization
- Proper failure recording

```bash
java -cp target/locust4j-*-jar-with-dependencies.jar \
    examples.task.ErrorHandlingExample 127.0.0.1 5557
```

#### `VirtualThreadsExample.java` ⭐ NEW
Demonstrates Java 21 virtual threads (enabled by default).
- Platform vs virtual threads comparison
- Memory usage optimization
- High-concurrency scenarios
- Threading mode detection

```bash
# Run with default (virtual threads enabled)
java -cp target/locust4j-*-jar-with-dependencies.jar \
     examples.task.VirtualThreadsExample 127.0.0.1 5557

# Or explicitly disable virtual threads to use platform threads
java -Dlocust4j.virtualThreads.enabled=false \
     -cp target/locust4j-*-jar-with-dependencies.jar \
     examples.task.VirtualThreadsExample 127.0.0.1 5557
```

### Rate Limiting (`ratelimit/`)

#### `RampUpRps.java`
Demonstrates gradual load increase.
- Ramp-up configuration
- Progressive load testing
- Threshold management

#### `StableRpsExample.java` ⭐ NEW
Shows consistent rate limiting.
- Fixed RPS (requests per second)
- Steady-state load testing
- Throughput control

```bash
java -cp target/locust4j-*-jar-with-dependencies.jar \
    examples.ratelimit.StableRpsExample 127.0.0.1 5557
```

### Task Weights (`taskset/`)

#### `WeighingRps.java`
Demonstrates task weight distribution.
- Multiple tasks with different weights
- Proportional execution
- Load distribution patterns

### Task Factories (`taskfactory/`)

#### `ThreadPerConnection.java`
Shows thread-per-connection pattern.
- Custom task factory implementation
- Connection pooling concepts
- Resource management

### Advanced Scenarios (`advanced/`)

#### `EcommerceLoadTest.java` ⭐ NEW
Real-world e-commerce simulation.
- Multiple realistic user behaviors
- Session management
- Shopping cart operations
- Payment processing simulation
- Business metrics tracking (orders, revenue)
- Weighted task distribution (browse 50%, view 30%, cart 15%, checkout 5%)

```bash
java -cp target/locust4j-*-jar-with-dependencies.jar \
    examples.advanced.EcommerceLoadTest 127.0.0.1 5557
```

## Feature Matrix

| Example | Rate Limiting | Error Handling | Multi-Task | Virtual Threads | Real-world |
|---------|---------------|----------------|------------|-----------------|------------|
| Main | ❌ | ❌ | ❌ | ❌ | ❌ |
| TaskAlwaysSuccess | ❌ | ❌ | ❌ | ❌ | ❌ |
| TaskAlwaysFail | ❌ | ✅ | ❌ | ❌ | ❌ |
| ErrorHandlingExample | ❌ | ✅✅✅ | ❌ | ❌ | ✅ |
| VirtualThreadsExample | ✅ | ❌ | ✅ | ✅✅✅ | ✅ |
| RampUpRps | ✅✅ | ❌ | ✅ | ❌ | ✅ |
| StableRpsExample | ✅✅ | ✅ | ❌ | ❌ | ✅ |
| WeighingRps | ✅ | ❌ | ✅✅ | ❌ | ❌ |
| ThreadPerConnection | ❌ | ❌ | ✅ | ❌ | ✅ |
| EcommerceLoadTest | ✅✅ | ✅✅ | ✅✅✅ | ❌ | ✅✅✅ |

Legend: ❌ Not covered, ✅ Basic, ✅✅ Intermediate, ✅✅✅ Advanced

## Common Patterns

### Pattern 1: Basic Task
```java
public class MyTask extends AbstractTask {
    @Override
    public int getWeight() {
        return 1;
    }

    @Override
    public String getName() {
        return "my-task";
    }

    @Override
    public void execute() throws Exception {
        long start = System.currentTimeMillis();
        try {
            // Your code here
            long elapsed = System.currentTimeMillis() - start;
            Locust.getInstance().recordSuccess("http", "GET /api", elapsed, 1024);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            Locust.getInstance().recordFailure("http", "GET /api", elapsed, e.getMessage());
        }
    }
}
```

### Pattern 2: Stable Rate Limiting
```java
StableRateLimiter limiter = new StableRateLimiter(100); // 100 RPS
Locust locust = Locust.getInstance();
locust.setMaxRPS(100);
locust.setRateLimiter(limiter);
```

### Pattern 3: Ramp-up Rate Limiting
```java
RampUpRateLimiter limiter = new RampUpRateLimiter(
    200,                    // maxThreshold: target 200 RPS
    10,                     // rampUpStep: increase by 10 RPS
    5,                      // rampUpPeriod: every 5 seconds
    TimeUnit.SECONDS,
    100,                    // refillPeriod: 100ms
    TimeUnit.MILLISECONDS
);
```

### Pattern 4: Error Handling with Retry
```java
private void executeWithRetry(int maxRetries) throws Exception {
    for (int i = 0; i <= maxRetries; i++) {
        try {
            doWork();
            return; // Success
        } catch (TransientException e) {
            if (i == maxRetries) throw e;
            Thread.sleep(100 * (i + 1)); // Exponential backoff
        }
    }
}
```

### Pattern 5: Virtual Threads (Java 21+)
```java
// Enable via system property
System.setProperty("locust4j.virtualThreads.enabled", "true");

// Or via JVM argument
// java -Dlocust4j.virtualThreads.enabled=true ...

// Virtual threads are used automatically for:
// - Worker pool
// - Stats processing
// - Rate limiters
// - RPC communication
```

## Best Practices

### 1. Always Record Metrics
```java
// Record success with response time and size
Locust.getInstance().recordSuccess("http", "GET /api", elapsed, responseSize);

// Record failure with error message
Locust.getInstance().recordFailure("http", "GET /api", elapsed, errorMessage);
```

### 2. Use Appropriate Weights
```java
// Heavy operations: lower weight
public int getWeight() { return 1; }

// Light operations: higher weight
public int getWeight() { return 10; }
```

### 3. Handle Exceptions Properly
```java
try {
    // Your code
} catch (SpecificException e) {
    // Handle specific errors
} catch (Exception e) {
    // Handle unexpected errors
    logger.error("Unexpected error", e);
} finally {
    // Cleanup resources
}
```

### 4. Use Rate Limiters
- **Stable**: For sustained load testing
- **Ramp-up**: For gradual load increase
- Always match `maxRPS` with rate limiter configuration

### 5. Enable Virtual Threads for High Concurrency
```bash
# For > 1000 concurrent users
java -Dlocust4j.virtualThreads.enabled=true \
     -Dlocust4j.virtualThreads.verbose=true \
     -jar your-app.jar
```

## Performance Tips

1. **Use Virtual Threads (Java 21+)**: 1000x memory reduction, 125x more concurrent users
2. **Batch Operations**: Group small operations to reduce overhead
3. **Connection Pooling**: Reuse connections (see `ThreadPerConnection.java`)
4. **Async I/O**: Use non-blocking operations when possible
5. **Monitor Memory**: Use `-Xmx` and `-Xms` appropriately

## Troubleshooting

### Issue: Out of Memory
**Solution**: Enable virtual threads or increase heap size
```bash
java -Xmx2g -Dlocust4j.virtualThreads.enabled=true -jar app.jar
```

### Issue: Low Throughput
**Solution**: Check rate limiter configuration
```java
// Make sure maxRPS matches your rate limiter
locust.setMaxRPS(1000);
rateLimiter = new StableRateLimiter(1000);
```

### Issue: Connection Timeouts
**Solution**: Implement retry logic (see `ErrorHandlingExample.java`)

### Issue: Tasks Not Executing
**Solution**: Ensure weights are > 0 and task names are unique

## Additional Resources

- [Main Documentation](../README.md)
- [Virtual Threads Guide](../VIRTUAL_THREADS_GUIDE.md)
- [API Documentation](https://javadoc.io/doc/com.github.myzhan/locust4j)
- [Locust Documentation](https://docs.locust.io/)

## Contributing

To add a new example:
1. Create a well-documented class with clear javadoc
2. Include usage instructions in the class header
3. Update this README with example description
4. Test with both platform and virtual threads
5. Follow existing naming conventions
