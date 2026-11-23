package com.codesprint.lesson0831.config;

import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ToolCallingConfigurationTest {

    @Autowired
    private ToolCallingRunner runner;

    @Test
    void shouldCreateRunnerAndReturnFakeAnswer() {
        String answer = runner.run(
                new ChatRequest(
                        "user-a",
                        "session-1",
                        "你好"
                ),
                List.of()
        );

        assertEquals(
                "[fake-test] 这是测试模型的回答：你好",
                answer
        );
    }
}