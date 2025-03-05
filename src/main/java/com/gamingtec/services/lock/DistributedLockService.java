package com.gamingtec.services.lock;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DistributedLockService {

  private final RedisTemplate<String, String> redisTemplate;

  // Maximum time (in milliseconds) that a thread will wait per iteration.
  private static final long MAX_WAIT_MS = 50;

  // Local monitor for thread wait/notify.
  private final Object monitor = new Object();

  /**
   * Attempts to acquire the lock using a simple retry mechanism.
   * Threads wait on a local monitor using wait/notify for a flexible duration.
   *
   * @param lockKey          The Redis key used for the lock.
   * @param lockOwner        The unique identifier representing the lock owner.
   * @param lockTimeout      The lock's expiration timeout as a Duration.
   * @param acquiringTimeout Maximum time to try acquiring the lock, as a Duration.
   * @return true if the lock was acquired successfully, false otherwise.
   */
  public boolean acquireLock(String lockKey, String lockOwner, Duration lockTimeout, Duration acquiringTimeout) {
    long endTime = System.currentTimeMillis() + acquiringTimeout.toMillis();
    while (System.currentTimeMillis() < endTime) {
      if (Boolean.TRUE.equals(
          redisTemplate.opsForValue().setIfAbsent(lockKey, lockOwner, lockTimeout.toMillis(), TimeUnit.MILLISECONDS)
      )) {
        return true;
      }
      long remaining = endTime - System.currentTimeMillis();
      if (remaining <= 0) break;
      long waitTime = Math.min(remaining, MAX_WAIT_MS);
      synchronized (monitor) {
        try {
          monitor.wait(waitTime);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return false;
  }

  /**
   * Attempts to acquire the lock using a fair locking algorithm.
   * The ordering of requests is determined by a unique lock request identifier (lockRequestOwner)
   * that is enqueued in a sorted set. Threads wait on a local monitor (via wait/notify) for a
   * flexible duration instead of a fixed sleep.
   *
   * @param lockKey          The Redis key used for the lock.
   * @param lockOwner        The unique identifier representing the lock owner (value stored in Redis).
   * @param lockRequestOwner The unique identifier representing the lock request (used solely for ordering).
   * @param lockTimeout      The lock's expiration timeout as a Duration.
   * @param acquiringTimeout Maximum time to try acquiring the lock, as a Duration.
   * @return true if the lock was acquired fairly, false otherwise.
   */
  public boolean acquireLockFair(String lockKey, String lockOwner, String lockRequestOwner,
                                 Duration lockTimeout, Duration acquiringTimeout) {
    String queueKey = lockKey + ":queue";
    String counterKey = lockKey + ":counter";

    // Obtain a unique ticket for ordering.
    Long ticket = redisTemplate.opsForValue().increment(counterKey);
    if (ticket == null) {
      return false;
    }
    // Add this request into the sorted set with the ticket as its score.
    redisTemplate.opsForZSet().add(queueKey, lockRequestOwner, ticket);

    long endTime = System.currentTimeMillis() + acquiringTimeout.toMillis();
    try {
      while (System.currentTimeMillis() < endTime) {
        // Retrieve the current head (lowest score) of the queue.
        Set<String> leaders = redisTemplate.opsForZSet().range(queueKey, 0, 0);
        if (leaders != null && !leaders.isEmpty() && leaders.iterator().next().equals(lockRequestOwner)) {
          // It is this request's turn; attempt to acquire the lock.
          if (Boolean.TRUE.equals(
              redisTemplate.opsForValue().setIfAbsent(lockKey, lockOwner, lockTimeout.toMillis(), TimeUnit.MILLISECONDS)
          )) {
            return true;
          }
        }
        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) break;
        long waitTime = Math.min(remaining, MAX_WAIT_MS);
        synchronized (monitor) {
          try {
            monitor.wait(waitTime);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
          }
        }
      }
      return false;
    } finally {
      // Always remove this request from the waiting queue.
      redisTemplate.opsForZSet().remove(queueKey, lockRequestOwner);
    }
  }

  /**
   * Releases the lock only if the provided lockOwner matches the value in Redis.
   * Upon a successful release, the local monitor is notified to wake up waiting threads.
   *
   * @param lockKey   The Redis key used for the lock.
   * @param lockOwner The unique identifier representing the lock owner.
   * @return true if the lock was released successfully, false otherwise.
   */
  public boolean releaseLock(String lockKey, String lockOwner) {
    String luaScript =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  local res = redis.call('del', KEYS[1]); " +
            "  return res; " +
            "else " +
            "  return 0; " +
            "end";
    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);
    long result = redisTemplate.execute(redisScript, Collections.singletonList(lockKey), lockOwner);
    if (result > 0) {
      // Notify all waiting threads.
      synchronized (monitor) {
        monitor.notifyAll();
      }
      return true;
    }
    return false;
  }
}
