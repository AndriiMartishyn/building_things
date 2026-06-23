package com.martishyn.auth.redis;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final Logger logger = LoggerFactory.getLogger(RateLimiterFilter.class);

    private final RedisRateLimiter redisRateLimiter;

    public RateLimiterFilter(RedisRateLimiter redisRateLimiter) {
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String userAddress = request.getRemoteAddr();
        boolean isAllowedRequest = redisRateLimiter.isAllowedRequest(userAddress);
        if (!isAllowedRequest) {
            logger.debug("User {} has been rate limited", userAddress);
            throw new TooManyRequestException("Too Many Requests");
        }
        filterChain.doFilter(request, response);
    }
}
