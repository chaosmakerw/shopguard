package com.codesprint.lesson0831.trade.demo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class TradeDemoOriginFilterTest {
    @Test void nonRootContextStillRejectsCrossOriginWrites() throws Exception {
        var request = new MockHttpServletRequest("POST", "/shopguard/api/trade/orders");
        request.setContextPath("/shopguard");
        request.setServletPath("/api/trade/orders");
        request.setServerName("127.0.0.1");
        request.addHeader("Host", "127.0.0.1:18085");
        request.addHeader("Origin", "https://other.example");
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        new TradeDemoOriginFilter().doFilter(request, response, (req, res) -> reached.set(true));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(reached).isFalse();
    }
}
