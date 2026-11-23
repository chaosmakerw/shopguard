package com.codesprint.lesson0831.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import com.codesprint.lesson0831.agent.toolcalling.ToolExecutionResult;
import com.codesprint.lesson0831.agent.toolcalling.ToolExchange;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ToolSchemas;
public class GatewayRequestMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();
    public ModelRequest toRequest(
            String model,
            ChatRequest request,
            List<ChatMessage> history
    ) {
        Objects.requireNonNull(request, "本次请求不能为空");
        Objects.requireNonNull(history, "历史消息不能为空");

        List<ModelMessage> messages = new ArrayList<>();

       for(ChatMessage message :history){
           String role = message.role();
           String content = message.content();
           ModelMessage modelMessage = new ModelMessage(role,content);
           messages.add(modelMessage);
       }
       ModelMessage currentMessage = new ModelMessage("user", request.question());
        messages.add(currentMessage);
       return new ModelRequest(model,messages);
    }
    public ModelRequest toRequest(
            String model,
            ModelInput input
    ) throws JsonProcessingException {

        Objects.requireNonNull(input, "模型输入不能为空");

       ModelRequest initial = toRequest(model,input.request(),input.history());
       List<ModelMessage> messages = new ArrayList<>(initial.messages());
       for(ToolExchange exchange :input.exchanges()){
           appendExchangeMessages(messages,exchange);
       }
       return new ModelRequest(model,messages,ToolSchemas.all());
    }
    public ModelMessage toAssistantMessage(ModelTurn turn)
            throws JsonProcessingException {

        Objects.requireNonNull(turn, "模型响应不能为空");

        List<ModelMessage.ToolCallPayload> payloads =
                new ArrayList<>();

        for (ToolCall call : turn.toolCalls()) {
            String argumentsJson =
                    objectMapper.writeValueAsString(call.arguments());
            ModelMessage.FunctionPayload functionPayload =
                    new ModelMessage.FunctionPayload(call.name(),argumentsJson);
            ModelMessage.ToolCallPayload toolCallPayload =
                    new ModelMessage.ToolCallPayload(call.id(),"function",functionPayload);
            payloads.add(toolCallPayload);
        }

        return new ModelMessage(
                "assistant",
                turn.text(),
                payloads,
                null
        );
    }
    public ModelMessage toToolMessage(ToolExecutionResult execution)
            throws JsonProcessingException {

        Objects.requireNonNull(execution, "工具执行结果不能为空");

        String content =
                objectMapper.writeValueAsString(execution.result());
        return new ModelMessage("tool",content,null,execution.toolCallId());

    }
    public void appendExchangeMessages(
            List<ModelMessage> messages,
            ToolExchange exchange
    ) throws JsonProcessingException {

        Objects.requireNonNull(messages, "消息列表不能为空");
        Objects.requireNonNull(exchange, "工具交互不能为空");

        messages.add(toAssistantMessage(exchange.turn()));
        for(ToolExecutionResult result :exchange.results()){
            ToolExecutionResult execution = result;
            messages.add(toToolMessage(execution));
        }
    }
}