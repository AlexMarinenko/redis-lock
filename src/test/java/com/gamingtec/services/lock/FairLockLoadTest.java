package com.gamingtec.services.lock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class FairLockLoadTest {

  @Autowired
  private DistributedLockService lockService;

  private static final String LOCK_KEY = "test_fair_lock";
  private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration ACQUIRING_TIMEOUT = Duration.ofSeconds(10);

  @Test
  public void testFairLockUnderLoad() throws InterruptedException, ExecutionException {
    int numThreads = 50;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numThreads);
    List<String> acquisitionOrder = Collections.synchronizedList(new ArrayList<>());

    List<Future<Boolean>> futures = new ArrayList<>();

    // Submit tasks simulating concurrent lock requests.
    for (int i = 0; i < numThreads; i++) {
      final int threadId = i;
      Future<Boolean> future = executor.submit(() -> {
        // Each thread uses its own lockOwner and lockRequestOwner.
        String lockOwner = "owner-" + threadId;
        String lockRequestOwner = "request-" + threadId;

        // Wait until all threads are ready.
        startLatch.await();

        boolean acquired = lockService.acquireLockFair(
            LOCK_KEY,
            lockOwner,
            lockRequestOwner,
            LOCK_TIMEOUT,
            ACQUIRING_TIMEOUT
        );

        if (acquired) {
          // Record acquisition order.
          acquisitionOrder.add(lockOwner);

          // Simulate work in the critical section.
          Thread.sleep(100);

          // Release the lock.
          lockService.releaseLock(LOCK_KEY, lockOwner);
        }
        doneLatch.countDown();
        return acquired;
      });
      futures.add(future);
    }

    // Start all threads at once.
    startLatch.countDown();

    // Wait until all threads complete.
    doneLatch.await();
    executor.shutdownNow();

    // Output the acquisition order and count.
    System.out.println("Acquisition order: " + acquisitionOrder);
    int acquiredCount = 0;
    for (Future<Boolean> f : futures) {
      if (f.get()) {
        acquiredCount++;
      }
    }
    System.out.println("Total acquired locks: " + acquiredCount);
  }
}
