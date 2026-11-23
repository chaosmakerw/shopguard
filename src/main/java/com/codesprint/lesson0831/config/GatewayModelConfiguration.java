package com.codesprint.lesson0831.config;

import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.GatewayChatModel;
import com.codesprint.lesson0831.agent.GatewayHttpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@Profile("gateway")
public class GatewayModelConfiguration {
    @Bean
    public ChatModel gatewayChatModel(ModelProperties properties) {
        URI endpoint = validatedEndpoint(properties.getBaseUrl());
        GatewayHttpTransport transport = new GatewayHttpTransport(endpoint, properties.getApiKey());
        return new GatewayChatModel(properties.getName(), transport, properties.getTimeout());
    }

    private URI validatedEndpoint(String value) {
        final URI endpoint;
        try {
            endpoint = URI.create(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Gateway 会话地址格式无效");
        }
        String scheme = endpoint.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Gateway 会话地址必须是无凭据、查询参数或片段的 HTTP(S) 地址");
        }
        return endpoint;
    }
}
