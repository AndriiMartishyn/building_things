package com.martishyn;

import com.martishyn.auth.redis.RedisRateLimiter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
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
        String IpAddress = "0.0.0.0";
        for (int i = 0; i < 10; i++) {
            Assertions.assertTrue(redisRateLimiter.isAllowedRequest(IpAddress));
        }
        Assertions.assertFalse(redisRateLimiter.isAllowedRequest(IpAddress));
        //10 request in 1 second
        //additional request in next second so its ok
        Thread.sleep(1100);
        Assertions.assertTrue(redisRateLimiter.isAllowedRequest(IpAddress));
    }
}
