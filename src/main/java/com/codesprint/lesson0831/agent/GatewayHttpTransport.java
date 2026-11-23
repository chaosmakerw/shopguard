package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.TimeBudget;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.ui.Model;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Dictionary;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GatewayHttpTransport {
    private final URI endpoint;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private static final ObjectReader RESPONSE_READER = new ObjectMapper()
            .readerFor(ModelResponse.class)
            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public GatewayHttpTransport(URI endpoint, String apiKey) {
        this(endpoint, apiKey, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    GatewayHttpTransport(URI endpoint, String apiKey, HttpClient httpClient) {
        this.endpoint = Objects.requireNonNull(endpoint);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("网关应用 Key 不能为空");
        }
        this.apiKey = apiKey;
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    public HttpRequest buildRequest(
            ModelRequest request,
            TimeBudget budget,
            String idempotencyKey
    ) throws JsonProcessingException {
        Objects.requireNonNull(request);
        Objects.requireNonNull(budget);
        budget.checkNotExpired();

        String body = objectMapper.writeValueAsString(request);

        long remainingMillis = budget.remainingMillis();

        return HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(remainingMillis))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .header("X-Trace-Id", com.codesprint.lesson0831.observability.AgentTrace.traceId())
                .header("X-Idempotency-Key", idempotencyKey)
                .header(
                        "X-Request-Timeout-Ms",
                        Long.toString(remainingMillis)
                )
                .POST(HttpRequest.BodyPublishers.ofString(
                        body,
                        StandardCharsets.UTF_8
                ))
                .build();
    }

    public ModelResponse send(ModelRequest request, TimeBudget budget) {
        if (com.codesprint.lesson0831.observability.AgentTrace.streaming()) {
            return new GatewayStreamTransport(endpoint, apiKey, httpClient).send(request, budget);
        }
        try {
            String idempotencyKey = UUID.randomUUID().toString();
            HttpRequest httpRequest =
                    buildRequest(request, budget, idempotencyKey);
            HttpResponse<String> response = sendWithinBudget(httpRequest, budget);

           budget.checkNotExpired();
           int status =response.statusCode();
           if(status<200||status>=300){
               throw new ModelCallException("网关请求失败");
           }
            var usage = objectMapper.readTree(response.body()).path("usage");
            var observation=objectMapper.readTree(response.body()).path("observation");
            if(observation.isObject())com.codesprint.lesson0831.observability.AgentTrace.gateway(objectMapper.convertValue(observation,new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>(){}));
            if (usage.path("prompt_tokens").canConvertToLong() && usage.path("completion_tokens").canConvertToLong()) {
                com.codesprint.lesson0831.observability.AgentTrace.usage(usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong());
            }
            ModelResponse result =
                    RESPONSE_READER.readValue(response.body());
            requireResponse(result);
            budget.checkNotExpired();
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ModelCallException("网关调用已中断", interrupted);
        } catch (TimeoutException timedOut) {
            throw new ModelCallException("网关调用超时", timedOut);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof HttpTimeoutException) {
                throw new ModelCallException("网关调用超时", failed.getCause());
            }
            throw new ModelCallException("网关调用失败", failed.getCause());
        } catch (JsonProcessingException invalidJson) {
            throw new ModelCallException("网关消息格式错误", invalidJson);
        }
    }

    private HttpResponse<String> sendWithinBudget(
            HttpRequest request, TimeBudget budget
    ) throws InterruptedException, ExecutionException, TimeoutException {
        budget.checkNotExpired();
        CompletableFuture<HttpResponse<String>> pending = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        try {
            return pending.get(budget.remainingMillis(), TimeUnit.MILLISECONDS);
        } finally {
            if (!pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    private void requireResponse(ModelResponse response) {
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().get(0) == null
                || response.choices().get(0).message() == null) {
            throw new ModelCallException("网关响应结构错误");
        }
    }
}
