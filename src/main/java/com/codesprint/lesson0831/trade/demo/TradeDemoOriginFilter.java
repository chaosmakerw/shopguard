package com.codesprint.lesson0831.trade.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

@Component
@Profile("trade-demo")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TradeDemoOriginFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getServletPath().startsWith("/api/trade/"); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String host = request.getServerName();
        String origin = request.getHeader("Origin");
        String expected = request.getScheme() + "://" + request.getHeader("Host");
        if (!Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host)
                || (origin != null && !origin.equals(expected))
                || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"ORIGIN_REJECTED\",\"message\":\"请从本机交易页面访问\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
