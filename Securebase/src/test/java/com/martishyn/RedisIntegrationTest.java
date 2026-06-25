package com.martishyn;

import com.martishyn.auth.redis.RedisRateLimiter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = AFTER_CLASS)
public class RedisIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:latest")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
    }

    @Autowired
    RedisRateLimiter redisRateLimiter;

    @Test
    public void blocksAfterLimit(){
        String IpAddress = "0.0.0.0";
        for (int i = 0; i < 9; i++) {
            Assertions.assertTrue(redisRateLimiter.isAllowedRequest(IpAddress));
        }
        Assertions.assertFalse(redisRateLimiter.isAllowedRequest(IpAddress));
    }

    @Test
    public void resetsAfterLimit() throws InterruptedException {
        String ipAddress = "0.0.0.0";
        for (int i = 0; i < 10; i++) {
            Assertions.assertTrue(redisRateLimiter.isAllowedRequest(ipAddress));
        }
        Assertions.assertFalse(redisRateLimiter.isAllowedRequest(ipAddress));
        //10 request in 1 second
        //additional request in next second so its ok
        Thread.sleep(1100);
        Assertions.assertTrue(redisRateLimiter.isAllowedRequest(ipAddress));
    }

    @Test
    public void concurrent_within_same_second_respects_limit() throws InterruptedException {
        String ipAddress = "0.0.0.0";
        int threadCount = 50;
        int limit = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (redisRateLimiter.isAllowedRequest(ipAddress)) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                    ;
                }
            });
        }
        startGate.countDown();
        boolean finished = endGate.await(5, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(finished).isTrue();
        assertThat(allowed.get()).isEqualTo(limit);                                                                                                                                                                                                                                              }
}
