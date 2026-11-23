package com.codesprint.lesson0831.trade.demo;

import com.codesprint.lesson0831.order.JdbcOrderService;
import com.codesprint.lesson0831.order.OrderService;
import com.codesprint.lesson0831.order.OrderTools;
import com.codesprint.lesson0831.product.JdbcProductService;
import com.codesprint.lesson0831.product.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实本地 HTTP、Spring 与隔离 H2 文件；固定用户、确定性模型、模拟支付。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "demo", "trade-demo"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TradeDemoApplicationIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DATABASE = Path.of("target", "trade-test-" + UUID.randomUUID(), "shopguard").toAbsolutePath().toString().replace('\\', '/');
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired OrderService orders;
    @Autowired ProductService products;
    @Autowired OrderTools orderTools;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:h2:file:" + DATABASE + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE");
        r.add("shopguard.trade.expiry-enabled", () -> "false");
    }
    @BeforeEach void clearTradeData() {
        var sql = new JdbcTemplate(dataSource);
        sql.update("DELETE FROM trade_payment_requests");
        sql.update("DELETE FROM trade_order_details");
        sql.update("DELETE FROM orders");
        sql.update("UPDATE trade_inventory SET available_quantity=5,reserved_quantity=0,sold_quantity=0 WHERE product_id=9001");
    }
    @Test void newOrderCanBeReadByOriginalCustomerServiceTools() throws Exception {
        assertThat(orders).isInstanceOf(JdbcOrderService.class);
        assertThat(products).isInstanceOf(JdbcProductService.class);
        var created = call("/api/trade/orders", "{\"productId\":\"9001\",\"quantity\":1}", null);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        String id = JSON.readTree(created.body()).path("orderId").asText();
        assertThat(orderTools.listMyOrders().data()).anyMatch(o -> o.orderId().toString().equals(id));
        var answer = call("/api/demo/workbench/chat", "{\"sessionId\":\"trade-test\",\"question\":\"我的订单\"}", null);
        assertThat(answer.statusCode()).isEqualTo(200);
        assertThat(answer.body()).contains(id, "PENDING_PAYMENT");
    }
    @Test void sameOriginBrowserCanCreateAndSimulatePayment() throws Exception {
        var created = call("/api/trade/orders", "{\"productId\":9001,\"quantity\":1}", base());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        String id = JSON.readTree(created.body()).path("orderId").asText();
        var paid = call("/api/trade/orders/" + id + "/simulate-payment", "{}", base());
        assertThat(paid.statusCode()).as(paid.body()).isEqualTo(200);
        assertThat(JSON.readTree(paid.body()).path("status").asText()).isEqualTo("PAID");
    }
    @Test void crossOriginBrowserCannotWriteLocalDemo() throws Exception {
        var response = call("/api/trade/orders", "{\"productId\":9001,\"quantity\":1}", "https://other.example");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(response.body()).path("code").asText()).isEqualTo("ORIGIN_REJECTED");
        assertThat(orders.listMyOrders(1001L)).isEmpty();
    }
    private String base() { return "http://127.0.0.1:" + port; }
    private HttpResponse<String> call(String path, String body, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) builder.header("Origin", origin);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
