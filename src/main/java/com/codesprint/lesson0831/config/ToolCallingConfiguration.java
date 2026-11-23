package com.codesprint.lesson0831.config;

import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallExecutor;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.order.OrderService;
import com.codesprint.lesson0831.order.OrderTools;
import com.codesprint.lesson0831.product.ProductTools;
import com.codesprint.lesson0831.product.ToolArgumentConverter;
import com.codesprint.lesson0831.product.ToolArgumentValidator;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.security.CurrentUserContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;
@Configuration
public class ToolCallingConfiguration {

    @Bean
    public ToolArgumentValidator toolArgumentValidator() {
        return new ToolArgumentValidator();
    }

    @Bean
    public ToolArgumentConverter toolArgumentConverter() {
        return new ToolArgumentConverter();
    }

    @Bean
    public OrderTools orderTools(
            CurrentUserContext currentUserContext,
            OrderService orderService
    ) {
        return new OrderTools(
                currentUserContext,
                orderService
        );
    }

    @Bean
    public ToolDispatcher toolDispatcher(
            ToolArgumentValidator validator,
            ToolArgumentConverter converter,
            ProductTools productTools,
            OrderTools orderTools,
            AgentOperationMetrics metrics
    ){
        return new ToolDispatcher(
                validator,
                converter,
                productTools,
                orderTools,
                metrics
        );
    }

    @Bean
    public ToolCallExecutor toolCallExecutor(
            ToolDispatcher dispatcher
    ) {
        return new ToolCallExecutor(dispatcher);
    }

    @Bean
    public ToolCallingRunner toolCallingRunner(
            ChatModel model,
            ToolCallExecutor executor,
            ModelProperties properties
    ) {
        return new ToolCallingRunner(
                model,
                executor,
                properties.getMaxToolRounds(),
                properties.getToolLoopTimeout()
        );
    }
}
