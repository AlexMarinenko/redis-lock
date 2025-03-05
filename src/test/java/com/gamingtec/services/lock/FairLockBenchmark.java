package com.gamingtec.services.lock;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for testing fair lock performance.
 */
@State(Scope.Benchmark)
public class FairLockBenchmark {

  public static void main(String[] args) throws Exception {
    Main.main(args);
  }

  private DistributedLockService lockService;
  private final String lockKey = "benchmark_fair_lock";

  @Setup(Level.Trial)
  public void setup() {
    // Set up Redis connection using Lettuce.
    LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", 6379);
    connectionFactory.afterPropertiesSet();

    RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(connectionFactory);
    // Ensure proper serialization.
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    redisTemplate.setValueSerializer(new StringRedisSerializer());
    redisTemplate.afterPropertiesSet();

    lockService = new DistributedLockService(redisTemplate);
  }

  /**
   * Benchmark method that repeatedly attempts to acquire the fair lock.
   * If acquired, the lock is immediately released.
   */
  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void benchmarkFairLockAcquisition() {
    // Generate unique identifiers for lock ownership and ordering.
    String lockOwner = UUID.randomUUID().toString();
    String lockRequestOwner = UUID.randomUUID().toString();
    Duration lockTimeout = Duration.ofSeconds(3);
    Duration acquiringTimeout = Duration.ofSeconds(2);

    boolean acquired = lockService.acquireLockFair(lockKey, lockOwner, lockRequestOwner, lockTimeout, acquiringTimeout);
    if (acquired) {
      lockService.releaseLock(lockKey, lockOwner);
    }
  }
}