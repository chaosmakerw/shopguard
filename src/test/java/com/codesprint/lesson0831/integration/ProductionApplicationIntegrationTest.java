package com.codesprint.lesson0831.integration;

import com.codesprint.lesson0831.agent.ChatMessage;
import com.codesprint.lesson0831.agent.ConversationKey;
import com.codesprint.lesson0831.agent.ConversationMemory;
import com.codesprint.lesson0831.demo.DemoController;
import com.codesprint.lesson0831.demo.DemoWorkbenchController;
import com.codesprint.lesson0831.order.JdbcOrderService;
import com.codesprint.lesson0831.order.OrderService;
import com.codesprint.lesson0831.product.JdbcProductService;
import com.codesprint.lesson0831.product.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * prod 应用装配测试：真实 Spring/Tomcat、本地双向 HTTP、RSA JWT 验签和 JDBC。
 * 默认 H2，显式提供隔离库 URL 时使用 MySQL；模型为本地协议服务器，不访问外部模型或 JWT 服务。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Import(ProductionApplicationIntegrationTest.TestJwtConfiguration.class)
@Timeout(20)
class ProductionApplicationIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ISSUER = "http://shopguard-test-issuer";
    private static final KeyPair SIGNING_KEY = signingKey();
    private static final List<JsonNode> MODEL_REQUESTS = new CopyOnWriteArrayList<>();
    private static final AtomicReference<Throwable> MODEL_SERVER_ERROR = new AtomicReference<>();
    private static final HttpServer MODEL_SERVER = startModelServer();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired ConversationMemory memory;
    @Autowired ProductService products;
    @Autowired OrderService orders;
    @Autowired ApplicationContext applicationContext;

    @DynamicPropertySource
    static void productionProperties(DynamicPropertyRegistry registry) {
        validateIsolatedMySqlTarget();
        registry.add("server.address", () -> "127.0.0.1");
        registry.add("app.model.base-url", () -> "http://127.0.0.1:" + MODEL_SERVER.getAddress().getPort());
        registry.add("app.model.api-key", () -> "integration-model-key");
        registry.add("app.model.name", () -> "local-protocol-fixture");
        registry.add("app.model.timeout", () -> "3s");
        registry.add("app.model.tool-loop-timeout", () -> "10s");
        registry.add("spring.datasource.url", () -> System.getProperty("shopguard.test.mysql.url",
                "jdbc:h2:mem:production_application;MODE=MySQL;DB_CLOSE_DELAY=-1"));
        registry.add("spring.datasource.driver-class-name", () -> usesMySql()
                ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver");
        registry.add("spring.datasource.username", () -> System.getProperty("shopguard.test.mysql.username", "sa"));
        registry.add("spring.datasource.password", () -> System.getProperty("shopguard.test.mysql.password", ""));
        registry.add("spring.sql.init.schema-locations", () -> usesMySql()
                ? "classpath:db/schema-mysql.sql" : "classpath:db/schema.sql");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
    }

    @BeforeEach
    void prepareDatabaseFixtures() throws Exception {
        MODEL_REQUESTS.clear();
        MODEL_SERVER_ERROR.set(null);
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("DELETE FROM orders WHERE order_id IN (101, 102, 103)");
            sql.executeUpdate("DELETE FROM products WHERE id = 42");
            sql.executeUpdate("INSERT INTO products VALUES (42, 'database-product-before', 'persistent product', 12.34, TRUE)");
            sql.executeUpdate("INSERT INTO orders VALUES (101, 1001, 'database-order-owner1001', 'PAID')");
            sql.executeUpdate("INSERT INTO orders VALUES (102, 1002, 'database-order-owner1002', 'CREATED')");
            sql.executeUpdate("INSERT INTO orders VALUES (103, 1001, 'cancelled-order-hidden', 'CANCELLED')");
        }
    }

    @AfterAll
    static void stopOwnedModelServer() {
        MODEL_SERVER.stop(0);
    }

    @Test
    void unauthenticatedRequestStopsBeforeModelAndConversation() throws Exception {
        String session = newSession();
        HttpResponse<String> response = ask(null, session, "我的订单");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(MODEL_REQUESTS).isEmpty();
        assertThat(memory.load(new ConversationKey("1001", session))).isEmpty();
    }

    @Test
    void invalidSignatureExpiredAndWrongIssuerTokensAreRejectedBeforeModel() throws Exception {
        String session = newSession();
        String expired = token("1001", ISSUER, Instant.now().minusSeconds(300));
        String wrongIssuer = token("1001", "http://untrusted-issuer", Instant.now().plusSeconds(300));
        String signed = validToken("1001");
        String invalidSignature = signed.substring(0, signed.lastIndexOf('.') + 1)
                + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[256]);

        assertThat(ask(expired, session, "我的订单").statusCode()).isEqualTo(401);
        assertThat(ask(wrongIssuer, session, "我的订单").statusCode()).isEqualTo(401);
        assertThat(ask(invalidSignature, session, "我的订单").statusCode()).isEqualTo(401);
        assertThat(MODEL_REQUESTS).isEmpty();
        assertThat(memory.load(new ConversationKey("1001", session))).isEmpty();
    }

    @Test
    void signedJwtControlsDatabaseOrdersAndConversationOwnership() throws Exception {
        String session = newSession();
        HttpResponse<String> first = post("/api/assistant/chat", validToken("1001"), Map.of(
                "sessionId", session, "question", "我的订单", "userId", "1002"));
        HttpResponse<String> second = ask(validToken("1002"), session, "我的订单");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(answer(first)).contains("database-order-owner1001")
                .doesNotContain("database-order-owner1002", "cancelled-order-hidden");
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(answer(second)).contains("database-order-owner1002")
                .doesNotContain("database-order-owner1001");
        assertThat(orders).isInstanceOf(JdbcOrderService.class);
        assertThat(memory.getClass().getSimpleName()).isEqualTo("JdbcConversationMemory");
        assertThat(memory.load(new ConversationKey("1001", session)))
                .extracting(ChatMessage::role).containsExactly("user", "assistant");
        assertThat(memory.load(new ConversationKey("1002", session)))
                .extracting(ChatMessage::role).containsExactly("user", "assistant");
        assertThat(MODEL_REQUESTS).hasSize(4);
        assertThat(MODEL_REQUESTS.get(2).path("messages")).hasSize(1);
        assertThat(lastToolResult().path("data").get(0).path("buyerId").asLong()).isEqualTo(1002L);
        HttpResponse<String> firstHistory = getHistory(validToken("1001"), session);
        HttpResponse<String> secondHistory = getHistory(validToken("1002"), session);
        assertThat(firstHistory.statusCode()).isEqualTo(200);
        assertThat(firstHistory.body()).contains("database-order-owner1001").doesNotContain("database-order-owner1002");
        assertThat(secondHistory.statusCode()).isEqualTo(200);
        assertThat(secondHistory.body()).contains("database-order-owner1002").doesNotContain("database-order-owner1001");
        assertThat(JSON.readTree(firstHistory.body())).hasSize(2);
        assertThat(getHistory(null, session).statusCode()).isEqualTo(401);
        assertThat(MODEL_SERVER_ERROR.get()).isNull();
    }

    @Test
    void modelSuppliedBuyerIdIsRejectedByRealDispatcher() throws Exception {
        HttpResponse<String> response = ask(validToken("1001"), newSession(), "forged buyerId 1002");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(answer(response)).contains("UNKNOWN_ARGUMENT")
                .doesNotContain("database-order-owner1001", "database-order-owner1002");
        JsonNode toolResult = lastToolResult();
        assertThat(toolResult.path("success").asBoolean()).isFalse();
        assertThat(toolResult.path("code").asText()).isEqualTo("UNKNOWN_ARGUMENT");
        assertThat(toolResult.path("data").isNull()).isTrue();
        assertThat(MODEL_REQUESTS).hasSize(2);
        assertThat(MODEL_SERVER_ERROR.get()).isNull();
    }

    @Test
    void productAnswerReflectsDatabaseChangesInsteadOfMemoryFixtures() throws Exception {
        HttpResponse<String> before = ask(validToken("1001"), newSession(), "查询数据库商品");
        assertThat(before.statusCode()).isEqualTo(200);
        assertThat(answer(before)).contains("database-product-before", "12.34");

        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE products SET title = 'database-product-after', price = 56.78 WHERE id = 42");
        }
        HttpResponse<String> after = ask(validToken("1001"), newSession(), "查询数据库商品");

        assertThat(after.statusCode()).isEqualTo(200);
        assertThat(answer(after)).contains("database-product-after", "56.78")
                .doesNotContain("database-product-before");
        assertThat(products).isInstanceOf(JdbcProductService.class);
        assertThat(MODEL_REQUESTS).hasSize(4);
        assertThat(MODEL_SERVER_ERROR.get()).isNull();
    }

    @Test
    void modelFailureAfterToolKeepsPreviousTurnAndAllowsRecovery() throws Exception {
        String session = newSession();
        ConversationKey key = new ConversationKey("1001", session);
        assertThat(ask(validToken("1001"), session, "我的订单").statusCode()).isEqualTo(200);
        List<ChatMessage> previous = List.copyOf(memory.load(key));

        HttpResponse<String> failed = ask(validToken("1001"), session, "工具后模型故障");

        assertThat(failed.statusCode()).isEqualTo(502);
        assertThat(JSON.readTree(failed.body()).path("message").asText()).isEqualTo("模型服务暂时不可用");
        assertThat(failed.body()).doesNotContain("integration-model-key", "upstream-internal-detail");
        assertThat(memory.load(key)).containsExactlyElementsOf(previous);
        assertThat(lastToolResult().path("data").get(0).path("productName").asText())
                .isEqualTo("database-order-owner1001");

        HttpResponse<String> recovered = ask(validToken("1001"), session, "我的订单");
        assertThat(recovered.statusCode()).isEqualTo(200);
        assertThat(memory.load(key)).hasSize(4).extracting(ChatMessage::sequence)
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(MODEL_SERVER_ERROR.get()).isNull();
    }

    @Test
    void productionProfileDoesNotRegisterDemoEndpoints() throws Exception {
        for (String path : List.of("/api/demo/info", "/api/demo/workbench/knowledge")) {
            HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(uri(path))
                    .header("Authorization", "Bearer " + validToken("1001"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path).isEqualTo(403);
        }
        assertThat(applicationContext.getBeansOfType(DemoController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(DemoWorkbenchController.class)).isEmpty();
        assertThat(MODEL_REQUESTS).isEmpty();
    }

    @Test
    void invalidInputDoesNotInvokeModelOrSaveConversation() throws Exception {
        String session = newSession();
        HttpResponse<String> response = ask(validToken("1001"), session, " ");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(MODEL_REQUESTS).isEmpty();
        assertThat(memory.load(new ConversationKey("1001", session))).isEmpty();
    }

    @Test
    void sessionsDifferingOnlyInLetterCaseOrTrailingSpaceRemainIsolatedInDatabase() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String upper = "CaseSession-" + suffix;
        String lower = "casesession-" + suffix;
        String trailingSpace = upper + " ";

        assertThat(ask(validToken("1001"), upper, "我的订单").statusCode()).isEqualTo(200);
        assertThat(ask(validToken("1001"), lower, "查询数据库商品").statusCode()).isEqualTo(200);
        assertThat(ask(validToken("1001"), trailingSpace, "forged buyerId 1002").statusCode()).isEqualTo(200);

        assertThat(memory.load(new ConversationKey("1001", upper))).hasSize(2)
                .extracting(ChatMessage::role).containsExactly("user", "assistant");
        assertThat(memory.load(new ConversationKey("1001", lower))).hasSize(2)
                .extracting(ChatMessage::role).containsExactly("user", "assistant");
        assertThat(memory.load(new ConversationKey("1001", trailingSpace))).hasSize(2)
                .extracting(ChatMessage::role).containsExactly("user", "assistant");
        assertThat(getHistory(validToken("1001"), upper).body()).contains("database-order-owner1001")
                .doesNotContain("database-product-before");
        assertThat(getHistory(validToken("1001"), lower).body()).contains("database-product-before")
                .doesNotContain("database-order-owner1001");
        assertThat(getHistory(validToken("1001"), trailingSpace).body()).contains("UNKNOWN_ARGUMENT")
                .doesNotContain("database-order-owner1001", "database-product-before");
        assertThat(MODEL_REQUESTS.get(2).path("messages")).hasSize(1);
        assertThat(MODEL_REQUESTS.get(4).path("messages")).hasSize(1);
        assertThat(MODEL_SERVER_ERROR.get()).isNull();
    }

    private HttpResponse<String> ask(String bearer, String session, String question) throws Exception {
        return post("/api/assistant/chat", bearer, Map.of("sessionId", session, "question", question));
    }

    private HttpResponse<String> post(String path, String bearer, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> getHistory(String bearer, String session) throws Exception {
        String encodedSession = java.net.URLEncoder.encode(session, StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/assistant/sessions/" + encodedSession + "/messages"))
                .timeout(Duration.ofSeconds(10)).GET();
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static boolean usesMySql() {
        return System.getProperty("shopguard.test.mysql.url") != null;
    }

    private static void validateIsolatedMySqlTarget() {
        String mysqlUrl = System.getProperty("shopguard.test.mysql.url");
        if (mysqlUrl == null) return;
        String safeFailure = "MySQL 应用验收只允许连接 shopguard_acceptance 或 shopguard_acceptance_ 开头的隔离库";
        try {
            if (!mysqlUrl.startsWith("jdbc:mysql://")) throw new IllegalArgumentException(safeFailure);
            URI target = URI.create(mysqlUrl.substring("jdbc:".length()));
            if (target.getHost() == null || target.getRawUserInfo() != null || target.getFragment() != null
                    || target.getRawPath() == null
                    || !target.getRawPath().matches("/shopguard_acceptance(?:_[A-Za-z0-9_]+)?")) {
                throw new IllegalArgumentException(safeFailure);
            }
            if (target.getRawQuery() != null) {
                for (String property : target.getRawQuery().split("&")) {
                    String key = java.net.URLDecoder.decode(property.split("=", 2)[0], StandardCharsets.UTF_8);
                    if (List.of("database", "databasename", "dbname", "catalog").stream()
                            .anyMatch(reserved -> reserved.equalsIgnoreCase(key))) {
                        throw new IllegalArgumentException(safeFailure);
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            // 不把潜在携带用户名、口令或其他敏感参数的原始 URL 放入失败消息。
            throw new IllegalArgumentException(safeFailure);
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String answer(HttpResponse<String> response) throws IOException {
        return JSON.readTree(response.body()).path("answer").asText();
    }

    private static String newSession() {
        return "prod-integration-" + UUID.randomUUID();
    }

    private static JsonNode lastToolResult() throws IOException {
        JsonNode messages = MODEL_REQUESTS.get(MODEL_REQUESTS.size() - 1).path("messages");
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("tool".equals(messages.get(i).path("role").asText())) {
                return JSON.readTree(messages.get(i).path("content").asText());
            }
        }
        throw new AssertionError("真实模型 HTTP 请求没有包含工具结果");
    }

    private static String validToken(String subject) throws Exception {
        return token(subject, ISSUER, Instant.now().plusSeconds(300));
    }

    private static String token(String subject, String issuer, Instant expiresAt) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
        String claims = encoder.encodeToString(JSON.writeValueAsBytes(Map.of(
                "sub", subject, "iss", issuer, "iat", expiresAt.minusSeconds(600).getEpochSecond(),
                "exp", expiresAt.getEpochSecond(), "roles", List.of("USER"))));
        String signingInput = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(SIGNING_KEY.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + encoder.encodeToString(signature.sign());
    }

    private static KeyPair signingKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static HttpServer startModelServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", ProductionApplicationIntegrationTest::respondAsModel);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respondAsModel(HttpExchange exchange) throws IOException {
        try {
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            MODEL_REQUESTS.add(request);
            JsonNode messages = request.path("messages");
            String question = "";
            for (JsonNode message : messages) {
                if ("user".equals(message.path("role").asText())) question = message.path("content").asText();
            }
            JsonNode last = messages.get(messages.size() - 1);
            if ("tool".equals(last.path("role").asText())) {
                if (question.contains("模型故障")) {
                    sendJson(exchange, 503, Map.of("error", "upstream-internal-detail"));
                } else {
                    sendJson(exchange, 200, Map.of("choices", List.of(Map.of("message",
                            Map.of("role", "assistant", "content", "数据库工具结果：" + last.path("content").asText())))));
                }
            } else {
                String name = question.contains("数据库商品") ? "get_product_detail" : "list_my_orders";
                String arguments = question.contains("forged") ? "{\"buyerId\":1002}"
                        : question.contains("数据库商品") ? "{\"productId\":42}" : "{}";
                sendJson(exchange, 200, Map.of("choices", List.of(Map.of("message", Map.of(
                        "role", "assistant", "tool_calls", List.of(Map.of(
                                "id", "prod-test-tool-call", "type", "function",
                                "function", Map.of("name", name, "arguments", arguments))))))));
            }
        } catch (Throwable exception) {
            MODEL_SERVER_ERROR.compareAndSet(null, exception);
            sendJson(exchange, 500, Map.of("error", "local-protocol-fixture-failed"));
        } finally {
            exchange.close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = JSON.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJwtConfiguration {
        @Bean
        JwtDecoder testJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) SIGNING_KEY.getPublic()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
