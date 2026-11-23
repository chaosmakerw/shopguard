package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.config.ModelProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Profile("(dev | test) & !gateway")
public class FakeChatModel implements ChatModel {

    private final ModelProperties properties;

    public FakeChatModel(ModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public String chat(ChatRequest request, List<ChatMessage> history) {
        return "[" + properties.getName() + "] 这是测试模型的回答：" + request.question();
    }
}
