package com.codesprint.lesson0831.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.model")
public class ModelProperties {

    @NotBlank
    @Pattern(
            regexp = "^(?!.*\\$\\{).+$",
            message = "baseUrl 不能包含未解析的环境变量占位符"
    )
    private String baseUrl;

    @NotBlank
    @Pattern(
            regexp = "^(?!.*\\$\\{).+$",
            message = "apiKey 不能包含未解析的环境变量占位符"
    )
    private String apiKey;

    @NotBlank
    @Pattern(
            regexp = "^(?!.*\\$\\{).+$",
            message = "name 不能包含未解析的环境变量占位符"
    )
    private String name;

    @NotNull
    @DurationMin(millis = 1)
    private Duration timeout = Duration.ofSeconds(10);

    @Min(0)
    @Max(10)
    private int maxToolRounds = 3;

    @NotNull
    @DurationMin(millis = 1)
    private Duration toolLoopTimeout = Duration.ofSeconds(30);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public Duration getToolLoopTimeout() {
        return toolLoopTimeout;
    }

    public void setToolLoopTimeout(Duration toolLoopTimeout) {
        this.toolLoopTimeout = toolLoopTimeout;
    }
}