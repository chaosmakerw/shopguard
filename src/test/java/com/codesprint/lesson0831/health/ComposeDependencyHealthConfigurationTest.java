package com.codesprint.lesson0831.health;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ComposeDependencyHealthConfigurationTest {

    private final ComposeDependencyHealthConfiguration configuration =
            new ComposeDependencyHealthConfiguration();

    @Test
    void jdbcProbeRunsARealQueryAndHidesConnectionFailures() {
        var healthy = configuration.mysqlHealthIndicator(
                "jdbc:h2:mem:health_probe;DB_CLOSE_DELAY=-1", "sa", "").health();
        assertThat(healthy.getStatus()).isEqualTo(Status.UP);

        var failed = configuration.mysqlHealthIndicator(
                "jdbc:mysql://127.0.0.1:1/secret_database?connectTimeout=200", "secret_user", "secret_password").health();
        assertThat(failed.getStatus()).isEqualTo(Status.DOWN);
        assertThat(failed.getDetails().toString()).contains("DEPENDENCY_UNAVAILABLE")
                .doesNotContain("secret_database", "secret_user", "secret_password");
    }

    @Test
    void redisProbeAuthenticatesThenRequiresPong() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            var reply = CompletableFuture.runAsync(() -> {
                try (var client = server.accept()) {
                    client.setSoTimeout(2000);
                    InputStream input = client.getInputStream();
                    byte[] request = new byte[128];
                    int count = input.read(request);
                    assertThat(new String(request, 0, count, StandardCharsets.UTF_8))
                            .contains("AUTH", "test-secret");
                    client.getOutputStream().write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
                    count = input.read(request);
                    assertThat(new String(request, 0, count, StandardCharsets.UTF_8))
                            .contains("PING");
                    client.getOutputStream().write("+PONG\r\n".getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            var health = configuration.redisHealthIndicator("127.0.0.1", server.getLocalPort(), "test-secret").health();
            reply.get(3, TimeUnit.SECONDS);
            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }
    }

    @Test
    void redisProbeMapsAuthenticationFailureToStableDownStatus()
            throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            var reply = CompletableFuture.runAsync(() -> {
                try (var client = server.accept()) {
                    client.setSoTimeout(2000);
                    byte[] request = new byte[128];
                    client.getInputStream().read(request);
                    client.getOutputStream().write(
                            "-ERR invalid password\r\n".getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });

            var health = configuration.redisHealthIndicator(
                    "127.0.0.1",
                    server.getLocalPort(),
                    "PRIVATE_REDIS_PASSWORD"
            ).health();

            reply.get(3, TimeUnit.SECONDS);
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails())
                    .containsEntry("probe", "AUTH_PING")
                    .containsEntry(
                            "reason",
                            "DEPENDENCY_UNAVAILABLE"
                    );
            assertThat(health.getDetails().toString())
                    .doesNotContain("PRIVATE_REDIS_PASSWORD");
        }
    }

    @Test
    void vectorProbeUsesAuthenticatedHealthEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/healthz", exchange -> {
            int status = "test-key".equals(exchange.getRequestHeaders().getFirst("api-key")) ? 200 : 401;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/healthz";
            assertThat(configuration.vectorStoreHealthIndicator(url, "test-key").health().getStatus())
                    .isEqualTo(Status.UP);
            assertThat(configuration.vectorStoreHealthIndicator(url, "wrong-key").health().getStatus())
                    .isEqualTo(Status.DOWN);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void modelProbeLabelsFakeAndDoesNotClaimUnknownModelsAreAvailable() {
        var fake = configuration.modelHealthIndicator("fake").health();
        assertThat(fake.getStatus()).isEqualTo(Status.UP);
        assertThat(fake.getDetails()).containsEntry("mode", "FAKE");
        assertThat(configuration.modelHealthIndicator("real").health().getStatus())
                .isEqualTo(Status.UNKNOWN);
    }
}
