package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.TimeBudget;
import java.util.Objects;
import java.util.List;

public interface ChatModel {

    // history 只包含此前的会话消息；本次问题由 request 提供。
    String chat(
            ChatRequest request,
            List<ChatMessage> history
    );
    default ModelTurn nextTurn(ModelInput input){
if(input ==null){
    throw new IllegalArgumentException("模型输入不能为空");
}
if(!input.exchanges().isEmpty()){
    throw new UnsupportedOperationException("当前模型尚不支持工具结果回传");
}
String answer = chat(input.request(),input.history());
return new ModelTurn(answer,List.of());
    }
    default ModelTurn nextTurn(ModelInput input, TimeBudget budget) {
        Objects.requireNonNull(budget, "调用预算不能为空");
        budget.checkNotExpired();
        return nextTurn(input);
    }
}
