package com.martishyn;

import com.martishyn.auth.redis.RedisRateLimiter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
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

    private static final Logger log = LoggerFactory.getLogger(RedisIntegrationTest.class);
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

    @Autowired
    RedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    public void blocksAfterLimit() {
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

    @RepeatedTest(10)
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
        assertThat(allowed.get()).isLessThanOrEqualTo(limit);
    }

    @RepeatedTest(10)
    public void testing_burst_between_window_should_be_broken_with_fixed_window() throws InterruptedException {
        String ipAddress = "0.0.0.0";
        long now = System.currentTimeMillis();
        long msBeforeNewSecond = 1000 - (now % 1000); //1000 - 147 = 853 ms before a new second
        Thread.sleep(msBeforeNewSecond - 50); //50 ms before boundary
        int allowedRequestsBeforeBoundary = 0;
        System.out.println("Before burst 1, second = " + (System.currentTimeMillis() / 1000));
        for (int i = 0; i < 10; i++) {
           boolean isAllowed =  redisRateLimiter.isAllowedRequest(ipAddress);
           if (isAllowed) allowedRequestsBeforeBoundary++;
        }
        System.out.println("After burst 1, second = " + (System.currentTimeMillis() / 1000));

        Thread.sleep(100);
        System.out.println("Before burst 2, second = " + (System.currentTimeMillis() / 1000));

        int allowedRequestsAfterBoundary = 0;
        for (int i = 0; i < 10; i++) {
            boolean isAllowed =  redisRateLimiter.isAllowedRequest(ipAddress);
            if (isAllowed) {
                allowedRequestsAfterBoundary++;
            }
        }
        System.out.println("After burst 2, second = " + (System.currentTimeMillis() / 1000));

        Assertions.assertEquals(10, allowedRequestsBeforeBoundary);
        Assertions.assertEquals(10, allowedRequestsAfterBoundary);
    }

    @Test
    public void should_not_pass_request_above_limit_in_window_1_000_ms() throws InterruptedException {
        String ipAddress = "0.0.0.0";
        long window = 1000;

        long now = System.currentTimeMillis();
        long msBeforeNewSecond = 1000 - (now % 1000);
        Thread.sleep(msBeforeNewSecond - 50); //50 ms before new boundary

        int allowedRequestsBeforeBoundary = 0;
        for (int i = 0; i < 10; i++) {
            boolean isAllowed =  redisRateLimiter.isAllowedRequestSlidingWindow(ipAddress, window);
            if (isAllowed) {
                allowedRequestsBeforeBoundary++;
            }
        }

        Thread.sleep(100);   // cross the wall-clock second boundary

        int allowedAfterBoundary = 0;
        for (int i = 0; i < 10; i++) {
            if (redisRateLimiter.isAllowedRequestSlidingWindow(ipAddress, window)) {
                allowedAfterBoundary++;
            }
        }

        // burst 1: 10 fresh requests inside an empty window -> all allowed
        Assertions.assertEquals(10, allowedRequestsBeforeBoundary);
        // burst 2: the burst-1 entries are only ~100-150ms old, still inside
        // the 1s window, so the ZSET already has ~10 entries -> nothing extra allowed
        Assertions.assertEquals(0, allowedAfterBoundary);
        // and the whole 200ms span never exceeded the limit
        assertThat(allowedRequestsBeforeBoundary + allowedAfterBoundary).isLessThanOrEqualTo(10);
    }

    @Test
    public void should_not_pass_request_above_limit_in_window_10_000_ms() throws InterruptedException {
        String ipAddress = "0.0.0.0";
        long window = 10000;

        int countedRequest = 0;
        for (int i = 0; i < 30; i++) {
            boolean isAllowed =  redisRateLimiter.isAllowedRequestSlidingWindow(ipAddress, window);
            if (isAllowed) {
                countedRequest++;
            }
        }

        assertThat(countedRequest).isEqualTo(10);

        Thread.sleep(window + 200);

        int countedReqInNewWindow = 0;
        for (int i = 0; i < 30; i++) {
            boolean isAllowed =  redisRateLimiter.isAllowedRequestSlidingWindow(ipAddress, window);
            if (isAllowed) {
                countedReqInNewWindow++;
            }
        }

        assertThat(countedReqInNewWindow).isEqualTo(10);

    }
}
