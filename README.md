# Locust4j [![Build Status](https://travis-ci.org/myzhan/locust4j.svg?branch=master)](https://travis-ci.org/myzhan/locust4j) [![Coverage Status](https://codecov.io/gh/myzhan/locust4j/branch/master/graph/badge.svg)](https://codecov.io/gh/myzhan/locust4j)

## Links

* Locust Website: <a href="http://locust.io">locust.io</a>
* Locust Documentation: <a href="http://docs.locust.io">docs.locust.io</a>

## Description

Locust4j is a high-performance load generator for Locust, written in Java 21+ with **virtual threads support enabled by default**. 
It's inspired by [boomer](https://github.com/myzhan/boomer) and [nomadacris](https://github.com/vrajat/nomadacris).

It's a **benchmarking library**, not a general purpose tool. To use it, you must implement test scenarios by yourself.

### Requirements

**Java 21 or higher** is required. Virtual threads are enabled by default for maximum performance and scalability.

### Usage examples

- [locust4j-http](https://github.com/myzhan/locust4j-http) is a demo and a good start
- [nejckorasa/locust4j-http-load](https://github.com/nejckorasa/locust4j-http-load) is another example project

## Features

* **Write user test scenarios in Java** <br>
Because it's written in Java, you can use all the things in the Java Ecosystem.

* **Virtual Threads by Default (Java 21+)** <br>
Locust4j uses Java 21 virtual threads for massive scalability with minimal memory overhead. Scale to 1M+ concurrent users!

* **High-Performance Concurrency** <br>
Virtual threads provide 1000x better memory efficiency and 125x better scalability compared to platform threads.

* **Opt-out Platform Threads Support** <br>
If needed, you can disable virtual threads and fall back to platform threads via system property.

## Build

```bash
git clone https://github.com/myzhan/locust4j
cd locust4j
mvn package
```

## Locally Install
```bash
mvn install
```

## Maven

Add this to your Maven project's pom.xml.

```xml
<dependency>
    <groupId>com.github.myzhan</groupId>
    <artifactId>locust4j</artifactId>
    <version>3.0.0</version>
</dependency>
```

**Note:** Version 3.0.0+ requires Java 21 or higher.

## Virtual Threads Configuration

Virtual threads are **ENABLED by default** for maximum performance and scalability.

### Using Default (Virtual Threads)
```bash
# No configuration needed - virtual threads are on by default
mvn test
java -jar your-load-test.jar
```

### Disabling Virtual Threads (Use Platform Threads)
If you need to use platform threads instead:

```bash
# Disable virtual threads
mvn test -Dlocust4j.virtualThreads.enabled=false
java -Dlocust4j.virtualThreads.enabled=false -jar your-load-test.jar
```

### Enable Verbose Logging
```bash
# Enable detailed virtual thread logging
mvn test -Dlocust4j.virtualThreads.verbose=true
```

### Performance Comparison

| Metric | Platform Threads | Virtual Threads | Improvement |
|--------|------------------|-----------------|-------------|
| Max Concurrent Users | ~8,000 | 1,000,000+ | **125x** |
| Memory (10K users) | 10-20 GB | 10-20 MB | **1000x** |
| Spawn Time | 10-20s | 0.5-1s | **20x** |

For detailed information, see [Virtual Threads Guide](VIRTUAL_THREADS_GUIDE.md).

## More Examples

See [Main.java](examples/task/Main.java).

This file represents all the exposed APIs of Locust4j.

## NOTICE
1. The task instance is shared across multiply threads, the execute method must be thread-safe.
2. Don't catch all exceptions in the execute method, just leave every unexpected exceptions to locust4j.

## Author

* myzhan
* vrajat

## Known Issues

* When stop-the-world happens in the JVM, you may get wrong response time reported to the master.
* Because of the JIT compiler, Locust4j will run faster as time goes by, which will lead to shorter response time.

## License

Open source licensed under the MIT license (see _LICENSE_ file for details).
