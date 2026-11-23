package com.codesprint.lesson0831.config;

import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.FakeChatModel;
import com.codesprint.lesson0831.agent.GatewayChatModel;
import com.codesprint.lesson0831.agent.ProdChatModel;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class GatewayModelConfigurationTest {
    private ApplicationContextRunner base() {
        return new ApplicationContextRunner().withUserConfiguration(
                Binding.class, RuntimeProfileConfiguration.class, GatewayModelConfiguration.class,
                FakeChatModel.class, ProdChatModel.class);
    }
    private ApplicationContextRunner configured(String profiles) {
        return base().withPropertyValues(
                "spring.profiles.active=" + profiles,
                "app.model.base-url=http://127.0.0.1:18081/api/gateway/chat",
                "app.model.api-key=local-test-key", "app.model.name=campus-chat", "app.model.timeout=3s");
    }

    @Test void developmentKeepsTheFakeModel() {
        configured("dev").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(FakeChatModel.class);
            assertThat(context).doesNotHaveBean(GatewayChatModel.class);
        });
    }
    @Test void testsKeepTheFakeModel() {
        configured("test").run(context -> assertThat(context)
                .hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(FakeChatModel.class));
    }
    @Test void productionKeepsTheDirectModel() {
        configured("prod").run(context -> assertThat(context)
                .hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(ProdChatModel.class));
    }
    @Test void developmentGatewaySelectsOneAdapter() {
        configured("dev,gateway").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(GatewayChatModel.class);
            assertThat(context).doesNotHaveBean(FakeChatModel.class).doesNotHaveBean(ProdChatModel.class);
        });
    }
    @Test void productionGatewaySelectsOneAdapter() {
        configured("prod,gateway").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(GatewayChatModel.class);
            assertThat(context).doesNotHaveBean(FakeChatModel.class).doesNotHaveBean(ProdChatModel.class);
        });
    }
    @Test void standaloneGatewayFails() {
        configured("gateway").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("gateway 必须与");
        });
    }
    @Test void gatewayCannotBeOverriddenByTheDemo() {
        configured("dev,demo,gateway").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("gateway 不得与 demo");
        });
    }
    @Test void gatewayMustFollowTheBaseProfile() {
        configured("gateway,dev").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("gateway 必须排在");
        });
    }
    @Test void aMissingApplicationKeyFailsInsteadOfSelectingAnotherModel() {
        configured("dev,gateway").withPropertyValues("app.model.api-key=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("apiKey");
        });
    }
    @Test void invalidEndpointsFailDuringStartup() {
        configured("dev,gateway").withPropertyValues("app.model.base-url=file:///tmp/model").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Gateway 会话地址必须是无凭据、查询参数或片段的 HTTP(S) 地址");
        });
    }
    @Test void gatewayYamlBindsItsOwnConnectionSettings() {
        base().withInitializer(new ConfigDataApplicationContextInitializer()).withPropertyValues(
                "spring.profiles.active=dev,gateway", "GATEWAY_API_KEY=local-test-key",
                "GATEWAY_CHAT_URL=http://127.0.0.1:18081/api/gateway/chat", "GATEWAY_MODEL=campus-chat"
        ).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(GatewayChatModel.class);
            ModelProperties properties = context.getBean(ModelProperties.class);
            assertThat(properties.getBaseUrl()).isEqualTo("http://127.0.0.1:18081/api/gateway/chat");
            assertThat(properties.getApiKey()).isEqualTo("local-test-key");
            assertThat(properties.getName()).isEqualTo("campus-chat");
        });
    }
    @Test void gatewayYamlDoesNotBorrowTheDevelopmentKeyWhenItsKeyIsMissing() {
        base().withInitializer(new ConfigDataApplicationContextInitializer()).withPropertyValues(
                "spring.profiles.active=dev,gateway", "GATEWAY_API_KEY="
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("apiKey");
        });
    }
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ModelProperties.class)
    static class Binding {
        @Bean AgentOperationMetrics metrics() { return AgentOperationMetrics.noop(); }
    }
}
