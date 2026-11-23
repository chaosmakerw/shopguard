package com.codesprint.lesson0831.trade.api;

import com.codesprint.lesson0831.order.JdbcOrderService;
import com.codesprint.lesson0831.order.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 Spring/Tomcat + HTTP + RSA JWT + H2；不调用模型或真实支付服务。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Import(TradeHttpIntegrationTest.LocalJwtConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(20)
class TradeHttpIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final KeyPair KEY = signingKey();
    private static final String ISSUER = "http://trade-integration-issuer";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired OrderService legacyOrders;
    @Autowired ApplicationContext context;
    private JdbcTemplate sql;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("server.address", () -> "127.0.0.1");
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:trade_http;MODE=MySQL;DB_CLOSE_DELAY=-1");
        r.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        r.add("spring.datasource.username", () -> "sa");
        r.add("spring.datasource.password", () -> "");
        r.add("spring.sql.init.schema-locations", () -> "classpath:db/schema.sql");
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        r.add("app.model.base-url", () -> "http://127.0.0.1:1");
        r.add("app.model.api-key", () -> "unused-local-test-key");
        r.add("app.model.name", () -> "unused-model");
        r.add("shopguard.trade.expiry-enabled", () -> "false");
    }

    @BeforeEach
    void fixtures() {
        sql = new JdbcTemplate(dataSource);
        sql.update("DELETE FROM trade_payment_requests");
        sql.update("DELETE FROM trade_order_details");
        sql.update("DELETE FROM trade_inventory");
        sql.update("DELETE FROM orders");
        sql.update("DELETE FROM products");
        sql.update("INSERT INTO products VALUES (79001, 'transaction-book', 'http fixture', 39.90, TRUE)");
        sql.update("INSERT INTO trade_inventory VALUES (79001, 5, 0, 0)");
    }

    @Test
    void authenticatedCreateReadCancelUsesTrustedUserAndLegacyOrderStore() throws Exception {
        var created = call("POST", "/api/trade/orders", "71001", key(), "{\"productId\":\"79001\",\"quantity\":2}");
        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode order = JSON.readTree(created.body());
        assertThat(order.path("orderId").isTextual()).isTrue();
        assertThat(order.path("productId").asText()).isEqualTo("79001");
        assertThat(order.path("totalAmount").asText()).isEqualTo("79.80");
        assertThat(order.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.has("buyerId")).isFalse();
        String id = order.path("orderId").asText();
        assertThat(legacyOrders).isInstanceOf(JdbcOrderService.class);
        assertThat(legacyOrders.listMyOrders(71001L)).anyMatch(o -> o.orderId().toString().equals(id));
        assertThat(legacyOrders.listMyOrders(71002L)).isEmpty();
        assertThat(call("GET", "/api/trade/orders/" + id, "71001", null, null).statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(call("GET", "/api/trade/orders", "71001", null, null).body()).path("items")).hasSize(1);
        assertThat(JSON.readTree(call("POST", "/api/trade/orders/" + id + "/cancel", "71001", null, "{}").body())
                .path("status").asText()).isEqualTo("CANCELLED");
        assertThat(stock()).isEqualTo(5);
        assertThat(legacyOrders.listMyOrders(71001L)).isEmpty();
    }

    @Test
    void retryKeepsOrderAndConflictingPayloadReturns409() throws Exception {
        String key = key();
        var first = call("POST", "/api/trade/orders", "71001", key, "{\"productId\":79001,\"quantity\":1}");
        var retry = call("POST", "/api/trade/orders", "71001", key, "{\"productId\":79001,\"quantity\":1}");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(retry.body()).isEqualTo(first.body());
        assertThat(call("POST", "/api/trade/orders", "71001", key, "{\"productId\":79001,\"quantity\":2}").statusCode()).isEqualTo(409);
        assertThat(stock()).isEqualTo(4);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void anonymousRequestsCannotReadOrChangeBusinessData() throws Exception {
        assertThat(call("GET", "/api/trade/products", null, null, null).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/api/trade/orders", null, key(), "{\"productId\":79001,\"quantity\":1}").statusCode()).isEqualTo(401);
        assertThat(stock()).isEqualTo(5);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void anotherUserCannotReadOrCancelOrder() throws Exception {
        String id = JSON.readTree(call("POST", "/api/trade/orders", "71001", key(), "{\"productId\":79001,\"quantity\":1}").body())
                .path("orderId").asText();
        assertThat(call("GET", "/api/trade/orders/" + id, "71002", null, null).statusCode()).isEqualTo(404);
        assertThat(call("POST", "/api/trade/orders/" + id + "/cancel", "71002", null, "{}").statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(call("GET", "/api/trade/orders", "71002", null, null).body()).path("items")).isEmpty();
        assertThat(stock()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"productId\":79001,\"quantity\":1,\"buyerId\":71002}",
            "{\"productId\":79001,\"quantity\":1,\"totalAmount\":0}",
            "{\"productId\":79001,\"quantity\":1.5}",
            "{\"productId\":79001,\"quantity\":1.0}",
            "{\"productId\":79001,\"quantity\":0}",
            "{\"productId\":79001,\"quantity\":21}",
            "{\"productId\":79001,\"quantity\":2147483648}",
            "{\"productId\":79001.1,\"quantity\":1}",
            "{\"productId\":9223372036854775808,\"quantity\":1}",
            "{\"productId\":\"9223372036854775808\",\"quantity\":1}",
            "{\"productId\":79001,\"quantity\":\"1\"}", "{}", "[]", "null", "{"
    })
    void malformedOrForgedPayloadStopsBeforeWriting(String body) throws Exception {
        var response = call("POST", "/api/trade/orders", "71001", key(), body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(JSON.readTree(response.body()).path("code").asText()).isEqualTo("INVALID_INPUT");
        assertThat(stock()).isEqualTo(5);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void missingIdempotencyKeyAndInvalidQueryAreStableErrors() throws Exception {
        assertThat(call("POST", "/api/trade/orders", "71001", null, "{\"productId\":79001,\"quantity\":1}").statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/trade/products?page=1.5", "71001", null, null).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/trade/orders/9223372036854775808", "71001", null, null).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/trade/products?size=51", "71001", null, null).statusCode()).isEqualTo(400);
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void productionDoesNotRegisterBuyerTriggeredPayment() throws Exception {
        assertThat(context.getBeansOfType(SimulatedPaymentController.class)).isEmpty();
        var info = JSON.readTree(call("GET", "/api/trade/context", "71001", null, null).body());
        assertThat(info.path("mode").asText()).isEqualTo("JWT");
        assertThat(info.path("userId").asText()).isEqualTo("71001");
        assertThat(info.path("simulatePaymentEnabled").asBoolean()).isFalse();
        assertThat(call("POST", "/api/trade/orders/123/simulate-payment", "71001", key(), "{}").statusCode()).isEqualTo(404);
    }

    @Test
    void pageIsPublicButInvalidTrustedSubjectCannotAccessApi() throws Exception {
        var page = call("GET", "/trade/", null, null, null);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("/trade/assets/");
        assertThat(call("GET", "/api/trade/context", "invalid-subject", null, null).statusCode()).isEqualTo(401);
    }

    private int stock() { return sql.queryForObject("SELECT available_quantity FROM trade_inventory WHERE product_id=79001", Integer.class); }
    private static String key() { return UUID.randomUUID().toString(); }
    private HttpResponse<String> call(String method, String path, String user, String key, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (user != null) request.header("Authorization", "Bearer " + token(user));
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private static String token(String user) throws Exception {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
        String claims = b64.encodeToString(JSON.writeValueAsBytes(Map.of("iss", ISSUER, "sub", user,
                "iat", Instant.now().minusSeconds(10).getEpochSecond(), "exp", Instant.now().plusSeconds(300).getEpochSecond(), "roles", List.of("USER"))));
        String input = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(KEY.getPrivate());
        signature.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + b64.encodeToString(signature.sign());
    }
    private static KeyPair signingKey() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class LocalJwtConfiguration {
        @Bean JwtDecoder localTradeJwtDecoder() {
            var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
