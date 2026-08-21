package com.neganote.bankapi.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1) // run very early in the filter chain
public class GeneralRequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        // Use an incoming X-Request-Id if present, otherwise generate one.
        // This lets upstream gateways propagate their request IDs.
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);

        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("traceId");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = requestId;
        }
        MDC.put("traceId", traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear(); // CRITICAL — Tomcat reuses threads
        }
    }
}
