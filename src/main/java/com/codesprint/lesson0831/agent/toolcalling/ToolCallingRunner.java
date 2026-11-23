
package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.agent.ChatMessage;
import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.ChatRequest;

import java.time.Duration;
import java.util.List;

public class ToolCallingRunner {

    private final ToolCallingLoop loop;
    private final Duration timeout;

    public ToolCallingRunner(
            ChatModel model,
            ToolCallExecutor executor,
            int maxToolRounds,
            Duration timeout
    ) {
        if (timeout == null) {
            throw new IllegalArgumentException("超时时间不能为空");
        }

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("超时时间必须大于零");
        }

        this.loop = new ToolCallingLoop(
                model,
                executor,
                maxToolRounds
        );
        this.timeout = timeout;
    }

    public String run(
            ChatRequest request,
            List<ChatMessage> history
    ) {
        ModelInput initialInput = new ModelInput(
                request,
                history,
                List.of()
        );

        return loop.run(
                initialInput,
                timeout,
                System::nanoTime
        );
    }
}
