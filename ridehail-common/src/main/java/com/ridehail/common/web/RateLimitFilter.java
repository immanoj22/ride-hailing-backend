package com.ridehail.common.web;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Equivalent of {@code express-rate-limit}'s default in-memory store (server.js:
 * {@code rateLimit({ windowMs: 5 * 60 * 1000, max: 100 })}). Per-instance/in-memory by design,
 * same as the original Node middleware - not shared across replicas.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final long windowMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = clientKey(request);
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > windowMillis) {
                return new Window(now);
            }
            return existing;
        });

        int count = window.count.incrementAndGet();
        if (count > maxRequests) {
            long resetAt = window.windowStart + windowMillis;
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "msg", "Too many requests, please try again later.",
                    "remainingRequests", 0,
                    "resetTime", Instant.ofEpochMilli(resetAt).toString())));
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
