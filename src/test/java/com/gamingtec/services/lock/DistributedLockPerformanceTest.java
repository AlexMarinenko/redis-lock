package com.gamingtec.services.lock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class DistributedLockPerformanceTest {

  @Autowired
  private DistributedLockService lockService;

  private static final String LOCK_KEY = "perf_test_lock";
  private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration ACQUIRING_TIMEOUT = Duration.ofSeconds(5);
  private static final int NUM_THREADS = 50;
  private static final Duration TEST_DURATION = Duration.ofSeconds(10);

  @Test
  public void performanceTestLock() throws InterruptedException, ExecutionException {
    ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch stopLatch = new CountDownLatch(NUM_THREADS);

    // Each thread collects its acquisition times (in nanoseconds).
    List<List<Long>> threadAcquisitionTimes = new ArrayList<>();
    for (int i = 0; i < NUM_THREADS; i++) {
      threadAcquisitionTimes.add(new ArrayList<>());
    }

    List<Future<Void>> futures = new ArrayList<>();
    long testEndTime = System.nanoTime() + TEST_DURATION.toNanos();

    for (int threadId = 0; threadId < NUM_THREADS; threadId++) {
      final int id = threadId;
      Future<Void> future = executor.submit(() -> {
        // Wait until all threads are ready.
        startLatch.await();
        while (System.nanoTime() < testEndTime) {
          String lockOwner = UUID.randomUUID().toString();
          long startTime = System.nanoTime();
          boolean acquired = lockService.acquireLock(
              LOCK_KEY,
              lockOwner,
              LOCK_TIMEOUT,
              ACQUIRING_TIMEOUT
          );
          long endTime = System.nanoTime();
          if (acquired) {
            long durationNanos = endTime - startTime;
            threadAcquisitionTimes.get(id).add(durationNanos);
            // Optionally, simulate some work in the critical section.
            lockService.releaseLock(LOCK_KEY, lockOwner);
          }
          // Optionally yield to reduce busy spinning.
          Thread.yield();
        }
        stopLatch.countDown();
        return null;
      });
      futures.add(future);
    }

    // Start all threads at once.
    startLatch.countDown();
    // Wait until all threads complete.
    stopLatch.await();
    executor.shutdownNow();

    // Aggregate performance metrics.
    long totalAcquisitions = 0;
    long totalTimeNanos = 0;
    long maxTime = 0;
    long minTime = Long.MAX_VALUE;
    for (List<Long> times : threadAcquisitionTimes) {
      for (Long t : times) {
        totalAcquisitions++;
        totalTimeNanos += t;
        if (t > maxTime) {
          maxTime = t;
        }
        if (t < minTime) {
          minTime = t;
        }
      }
    }
    double averageTimeMillis = totalAcquisitions == 0 ? 0 : (totalTimeNanos / (double) totalAcquisitions) / 1_000_000;
    System.out.println("Total acquisitions: " + totalAcquisitions);
    System.out.println("Average acquisition time: " + averageTimeMillis + " ms");
    System.out.println("Min acquisition time: " + (minTime / 1_000_000.0) + " ms");
    System.out.println("Max acquisition time: " + (maxTime / 1_000_000.0) + " ms");
  }
}