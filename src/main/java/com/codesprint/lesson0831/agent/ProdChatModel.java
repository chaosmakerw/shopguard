package com.codesprint.lesson0831.agent;
import com.codesprint.lesson0831.config.ModelProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import java.util.Objects;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ToolExchange;
import com.codesprint.lesson0831.agent.toolcalling.ToolExecutionResult;
import com.codesprint.lesson0831.agent.toolcalling.ToolSchemas;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
@Component
@Profile("prod & !gateway")
public class ProdChatModel implements ChatModel {
    private final ModelProperties properties;
    private final RestClient restClient;
    private final AgentOperationMetrics metrics;
    private static final ObjectReader ARGUMENT_READER =
            new ObjectMapper()
                    .readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final ObjectWriter JSON_WRITER =
            new ObjectMapper().writer();
    private static final Logger log =
            LoggerFactory.getLogger(
                    ProdChatModel.class
            );
    @Autowired
    public ProdChatModel(
            ModelProperties properties,
            AgentOperationMetrics metrics
    ) {
        this(
                properties,
                createRestClient(properties),
                metrics
        );
    }
    public ProdChatModel(
            ModelProperties properties
    ) {
        this(
                properties,
                createRestClient(properties),
                AgentOperationMetrics.noop()
        );
    }
    ProdChatModel(
            ModelProperties properties,
            RestClient restClient
    ) {
        this(
                properties,
                restClient,
                AgentOperationMetrics.noop()
        );
    }

    ProdChatModel(
            ModelProperties properties,
            RestClient restClient,
            AgentOperationMetrics metrics
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.metrics = Objects.requireNonNull(
                metrics,
                "指标组件不能为空"
        );
    }

    private static RestClient createRestClient(
            ModelProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                properties.getTimeout()
        );

        requestFactory.setReadTimeout(
                properties.getTimeout()
        );

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override

    public String chat(ChatRequest request, List<ChatMessage> history) {
        String baseUrl = properties.getBaseUrl();
        String apiKey = properties.getApiKey();
        String authorizationHeader = "Bearer "+apiKey;
        String modelName = properties.getName();
        String question = request.question();
        List<ModelMessage> messages = new ArrayList<>();
        for (ChatMessage message : history) {
            messages.add(new ModelMessage(message.role(), message.content()));
        }
        messages.add(new ModelMessage("user", question));
        ModelRequest modelRequest = new ModelRequest(modelName, messages);
        try {
            ModelResponse response = restClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", authorizationHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(modelRequest)
                    .retrieve()
                    .body(ModelResponse.class);
            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()
                    || response.choices().get(0).message() == null
                    || response.choices().get(0).message().content() == null) {
                throw new ModelCallException("模型返回了空结果");
            }
            return response.choices()
                    .get(0)
                    .message()
                    .content();

        } catch (ModelCallException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ModelCallException(
                    "模型服务调用失败",
                    exception
            );
        }

    }
    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            throw new ModelCallException("模型工具参数格式错误");
        }

        try {
            Map<String, Object> arguments = ARGUMENT_READER.readValue(json);

            if (arguments == null) {
                throw new ModelCallException("模型工具参数格式错误");
            }

            return arguments;
        } catch (JsonProcessingException exception) {
            throw new ModelCallException(
                    "模型工具参数格式错误",
                    exception
            );
        }
    }
    ModelTurn toModelTurn(ModelMessage message) {
        if (message == null) {
            throw new ModelCallException("模型返回了空响应");
        }

        List<ModelMessage.ToolCallPayload> payloads =
                message.toolCalls();

        if (payloads == null || payloads.isEmpty()) {
            if (message.content() == null || message.content().isBlank()) {
                throw new ModelCallException("模型返回了空响应");
            }

            return new ModelTurn(message.content(), List.of());
        }

        List<ToolCall> calls = new ArrayList<>();

        for (ModelMessage.ToolCallPayload payload : payloads) {
            if (payload == null
                    || !"function".equals(payload.type())
                    || payload.function() == null
                    || payload.function().name() == null
                    || payload.function().name().isBlank()) {
                throw new ModelCallException("模型工具调用格式错误");
            }

            Map<String, Object> arguments =
                    parseArguments(payload.function().arguments());

            calls.add(new ToolCall(
                    payload.id(),
                    payload.function().name(),
                    arguments
            ));
        }

        return new ModelTurn(message.content(), calls);
    }
    ModelRequest toToolRequest(ModelInput input) {
        if (input == null) {
            throw new IllegalArgumentException("模型输入不能为空");
        }

        List<ModelMessage> messages = new ArrayList<>();

        for (ChatMessage message : input.history()) {
            messages.add(new ModelMessage(
                    message.role(), message.content()
            ));
        }

        messages.add(new ModelMessage(
                "user", input.request().question()
        ));

        try {
            for (ToolExchange exchange : input.exchanges()) {
                List<ModelMessage.ToolCallPayload> calls =
                        new ArrayList<>();

                for (ToolCall call : exchange.turn().toolCalls()) {
                    calls.add(new ModelMessage.ToolCallPayload(
                            call.id(),
                            "function",
                            new ModelMessage.FunctionPayload(
                                    call.name(),
                                    JSON_WRITER.writeValueAsString(
                                            call.arguments()
                                    )
                            )
                    ));
                }

                messages.add(new ModelMessage(
                        "assistant",
                        exchange.turn().text(),
                        calls,
                        null
                ));

                for (ToolExecutionResult result : exchange.results()) {
                    messages.add(new ModelMessage(
                            "tool",
                            JSON_WRITER.writeValueAsString(result.result()),
                            null,
                            result.toolCallId()
                    ));
                }
            }
        } catch (JsonProcessingException exception) {
            throw new ModelCallException(
                    "工具交互序列化失败", exception
            );
        }

        return new ModelRequest(
                properties.getName(),
                messages,
                ToolSchemas.all()
        );
    }
    @Override
    public ModelTurn nextTurn(ModelInput input) {
        long startNanos = System.nanoTime();

        try {
            ModelRequest request =
                    toToolRequest(input);

            ModelResponse response = restClient.post()
                    .uri("/v1/chat/completions")
                    .header(
                            "Authorization",
                            "Bearer " + properties.getApiKey()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ModelResponse.class);

            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()
                    || response.choices().get(0) == null) {
                throw new ModelCallException(
                        "模型返回了空响应"
                );
            }

            ModelTurn turn = toModelTurn(
                    response.choices()
                            .get(0)
                            .message()
            );

            logModelSuccess(startNanos);

            return turn;
        } catch (ModelCallException exception) {
            logModelFailure(
                    startNanos,
                    exception
            );
            throw exception;
        } catch (RestClientException exception) {
            logModelFailure(
                    startNanos,
                    exception
            );
            throw new ModelCallException(
                    "模型服务调用失败",
                    exception
            );
        }
    }
    private void logModelSuccess(
            long startNanos
    ) {
        long elapsedNanos =
                System.nanoTime() - startNanos;

        metrics.record(
                "model",
                "SUCCESS",
                elapsedNanos
        );

        long latencyMs =
                elapsedNanos / 1_000_000;

        log.info(
                "model call requestId={} latencyMs={} resultCode=SUCCESS",
                currentRequestId(),
                latencyMs
        );
    }

    private void logModelFailure(
            long startNanos,
            RuntimeException exception
    ) {
        long elapsedNanos =
                System.nanoTime() - startNanos;

        metrics.record(
                "model",
                "MODEL_UNAVAILABLE",
                elapsedNanos
        );

        long latencyMs =
                elapsedNanos / 1_000_000;

        log.warn(
                "model call requestId={} latencyMs={} resultCode=MODEL_UNAVAILABLE exceptionType={}",
                currentRequestId(),
                latencyMs,
                exception.getClass().getName()
        );
    }

    private String currentRequestId() {
        String requestId = MDC.get("requestId");

        if (requestId == null || requestId.isBlank()) {
            return "UNAVAILABLE";
        }

        return requestId;
    }
}
