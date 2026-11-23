package com.codesprint.lesson0831.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;

/** Checks the four dependencies that the local Compose stack actually starts. */
@Configuration
@Profile("compose")
public class ComposeDependencyHealthConfiguration {

    @Bean("mysql")
    HealthIndicator mysqlHealthIndicator(
            @Value("${app.health.mysql.url}") String url,
            @Value("${app.health.mysql.username}") String username,
            @Value("${app.health.mysql.password}") String password
    ) {
        return () -> check("JDBC_QUERY", () -> {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("MySQL probe failed");
                }
            }
        });
    }

    @Bean("redis")
    HealthIndicator redisHealthIndicator(
            @Value("${app.health.redis.host}") String host,
            @Value("${app.health.redis.port}") int port,
            @Value("${app.health.redis.password}") String password
    ) {
        return () -> check("AUTH_PING", () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2000);
                socket.setSoTimeout(2000);
                OutputStream output = socket.getOutputStream();
                BufferedReader input = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                output.write(("*2\r\n$4\r\nAUTH\r\n$" +
                        password.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                        password + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
                if (!"+OK".equals(input.readLine())) {
                    throw new IllegalStateException("Redis authentication failed");
                }
                output.write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                if (!"+PONG".equals(input.readLine())) {
                    throw new IllegalStateException("Redis ping failed");
                }
            }
        });
    }

    @Bean("vectorStore")
    HealthIndicator vectorStoreHealthIndicator(
            @Value("${app.health.vector.url}") String url,
            @Value("${app.health.vector.api-key}") String apiKey
    ) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        return () -> check("QDRANT_HEALTHZ", () -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .header("api-key", apiKey)
                    .GET()
                    .build();
            if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 200) {
                throw new IllegalStateException("Qdrant health check failed");
            }
        });
    }

    @Bean("model")
    HealthIndicator modelHealthIndicator(
            @Value("${app.health.model.mode}") String mode
    ) {
        return () -> "fake".equalsIgnoreCase(mode)
                ? Health.up().withDetail("mode", "FAKE").build()
                : Health.unknown().withDetail("mode", "NOT_VERIFIED").build();
    }

    private Health check(String probe, CheckedProbe action) {
        try {
            action.run();
            return Health.up().withDetail("probe", probe).build();
        } catch (Exception ignored) {
            // Never expose JDBC URLs, credentials, internal hosts, or exception text.
            return Health.down().withDetail("probe", probe)
                    .withDetail("reason", "DEPENDENCY_UNAVAILABLE").build();
        }
    }

    @FunctionalInterface
    private interface CheckedProbe {
        void run() throws Exception;
    }
}
