package com.quickstart.template.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstart.template.shared.dto.MessageResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter for all /api/auth/** endpoints.
 * Limits each IP to {@code maxRequests} requests within {@code windowSeconds}.
 * Runs before the Spring Security filter chain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    @Value("${app.auth.rate-limit.max-requests:20}")
    private int maxRequests;

    @Value("${app.auth.rate-limit.window-seconds:300}")
    private int windowSeconds;

    private final ConcurrentHashMap<String, Deque<Long>> timestamps = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        boolean[] limited = {false};
        timestamps.compute(ip, (key, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            long cutoff = now - windowMs;
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                limited[0] = true;
            } else {
                deque.addLast(now);
            }
            return deque;
        });

        if (limited[0]) {
            log.warn("Rate limit exceeded for IP {} on {}", ip, request.getRequestURI());
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    new MessageResponse("请求过于频繁，请稍后再试"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
