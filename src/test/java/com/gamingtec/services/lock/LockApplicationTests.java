package com.gamingtec.services.lock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LockApplicationTests {

  @Autowired
  private DistributedLockService lockService;

  @Test
  void tryAcquireSequentially() {

    // Arrange
    String lockKey = "my_resource_lock1";
    String lockOwner = UUID.randomUUID().toString();
    String lockRequestOwner = UUID.randomUUID().toString();
    Duration lockTimeout = Duration.ofSeconds(2);
    Duration acquiringTimeout = Duration.ofSeconds(1);

    String lockOwner2 = UUID.randomUUID().toString();
    String lockRequestOwner2 = UUID.randomUUID().toString();
    Duration acquiringTimeout2 = Duration.ofSeconds(3);

    assertTrue(lockService.acquireLockFair(lockKey, lockOwner, lockRequestOwner, lockTimeout, acquiringTimeout));
    assertTrue(lockService.acquireLockFair(lockKey, lockOwner2, lockRequestOwner2, lockTimeout, acquiringTimeout2));
  }

  @Test
  void tryAcquireConflict() {

    // Arrange
    String lockKey = "my_resource_lock2";
    String lockOwner = UUID.randomUUID().toString();
    String lockRequestOwner = UUID.randomUUID().toString();
    Duration lockTimeout = Duration.ofSeconds(2);
    Duration acquiringTimeout = Duration.ofSeconds(1);

    String lockOwner2 = UUID.randomUUID().toString();
    String lockRequestOwner2 = UUID.randomUUID().toString();
    Duration acquiringTimeout2 = Duration.ofSeconds(1);

    assertTrue(lockService.acquireLockFair(lockKey, lockOwner, lockRequestOwner, lockTimeout, acquiringTimeout));
    assertFalse(lockService.acquireLockFair(lockKey, lockOwner2, lockRequestOwner2, lockTimeout, acquiringTimeout2));
  }

  @Test
  void tryRelease() {

    // Arrange
    String lockKey = "my_resource_lock3";
    String lockOwner = UUID.randomUUID().toString();
    String lockRequestOwner = UUID.randomUUID().toString();
    Duration lockTimeout = Duration.ofSeconds(2);
    Duration acquiringTimeout = Duration.ofSeconds(1);

    assertTrue(lockService.acquireLockFair(lockKey, lockOwner, lockRequestOwner, lockTimeout, acquiringTimeout));
    assertTrue(lockService.releaseLock(lockKey, lockOwner));

  }

  @Test
  void tryReleaseConflict() {

    // Arrange
    String lockKey = "my_resource_lock4";
    String lockOwner = UUID.randomUUID().toString();
    String lockRequestOwner = UUID.randomUUID().toString();
    Duration lockTimeout = Duration.ofSeconds(2);
    Duration acquiringTimeout = Duration.ofSeconds(1);

    String lockOwner2 = UUID.randomUUID().toString();
    String lockRequestOwner2 = UUID.randomUUID().toString();
    Duration acquiringTimeout2 = Duration.ofSeconds(3);

    assertTrue(lockService.acquireLockFair(lockKey, lockOwner, lockRequestOwner, lockTimeout, acquiringTimeout));
    assertFalse(lockService.releaseLock(lockKey, lockOwner2));

  }



}
