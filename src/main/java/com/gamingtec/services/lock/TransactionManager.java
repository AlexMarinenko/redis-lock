package com.gamingtec.services.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionManager {

  private final DistributedLockService distributedLockService;

  public void handleTransaction() {
    try (DistributedLockService lock = distributedLockService.acquireLock("transaction")) {
      // do some transactional work
      transaction.execute();

    } finally {
        // release the lock
      distributedLockService.releaseLock("transaction");
    }
  }
}
