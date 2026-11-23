package com.codesprint.lesson0831.config;

import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.agent.FakeChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
@ActiveProfiles("test")
class ModelPropertiesTest {

    @Autowired
    private ModelProperties properties;

    @Autowired
    private FakeChatModel fakeChatModel;

    @Test
    void shouldBindTestProfileProperties() {
        assertEquals(
                "http://test-model",
                properties.getBaseUrl()
        );
        assertEquals(
                "test-key",
                properties.getApiKey()
        );
        assertEquals(
                "fake-test",
                properties.getName()
        );
        assertEquals(
                Duration.ofSeconds(3),
                properties.getTimeout()
        );
        assertEquals(
                3,
                properties.getMaxToolRounds()
        );
        assertEquals(
                Duration.ofSeconds(10),
                properties.getToolLoopTimeout()
        );
    }

    @Test
    void fakeModelShouldUseNameFromConfiguration() {
        String answer = fakeChatModel.chat(
                new ChatRequest(
                        "user-a",
                        "session-1",
                        "配置从哪里来？"
                ),
                List.of()
        );

        assertEquals(
                "[fake-test] 这是测试模型的回答：配置从哪里来？",
                answer
        );
    }

    @Test
    void fakeModelShouldWorkWithoutSpringContext() {
        ModelProperties modelProperties =
                new ModelProperties();

        modelProperties.setName("unit-test");

        FakeChatModel model =
                new FakeChatModel(modelProperties);

        String answer = model.chat(
                new ChatRequest(
                        "user-a",
                        "session-1",
                        "你好"
                ),
                List.of()
        );

        assertEquals(
                "[unit-test] 这是测试模型的回答：你好",
                answer
        );
    }

    @Test
    void testProfileShouldUseSafeFakeConfiguration() {
        assertEquals(
                "http://test-model",
                properties.getBaseUrl()
        );
        assertEquals(
                "test-key",
                properties.getApiKey()
        );
        assertEquals(
                "fake-test",
                properties.getName()
        );
        assertNotEquals(
                "prod",
                properties.getName()
        );
    }
}