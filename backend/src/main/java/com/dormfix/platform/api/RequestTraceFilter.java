package com.dormfix.platform.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String TRACE_ATTRIBUTE = "dormfix.traceId";
    private static final Logger LOG = LoggerFactory.getLogger(RequestTraceFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        response.setHeader("X-Request-ID", traceId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                LOG.atInfo().addKeyValue("httpMethod", request.getMethod())
                        .addKeyValue("httpStatus", response.getStatus())
                        .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                        .log("HTTP request completed");
            }
        }
    }
}
