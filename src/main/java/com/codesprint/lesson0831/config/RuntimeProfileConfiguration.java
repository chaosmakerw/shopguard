package com.codesprint.lesson0831.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** 避免混合 Profile 让生产环境装入 permitAll 或固定身份。 */
@Configuration
public class RuntimeProfileConfiguration {
    @Bean
    public static BeanFactoryPostProcessor runtimeProfileGuard(Environment environment) {
        return beanFactory -> {
            if (environment.acceptsProfiles(Profiles.of("prod"))
                    && environment.acceptsProfiles(Profiles.of("dev | test | demo"))) {
                throw new IllegalStateException("prod 不得与 dev、test 或 demo 同时启用");
            }
            if (environment.acceptsProfiles(Profiles.of("demo"))
                    && !environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException("demo 必须与 dev 同时启用");
            }
            if (environment.acceptsProfiles(Profiles.of("gateway"))) {
                if (environment.acceptsProfiles(Profiles.of("demo"))) {
                    throw new IllegalStateException("gateway 不得与 demo 同时启用");
                }
                String[] active = environment.getActiveProfiles();
                int gatewayIndex = -1;
                int baseIndex = -1;
                for (int i = 0; i < active.length; i++) {
                    if ("gateway".equals(active[i])) {
                        gatewayIndex = i;
                    }
                    if ("dev".equals(active[i]) || "test".equals(active[i]) || "prod".equals(active[i])) {
                        baseIndex = i;
                    }
                }
                if (baseIndex < 0) {
                    throw new IllegalStateException("gateway 必须与 dev、test 或 prod 一起启用");
                }
                if (gatewayIndex < baseIndex) {
                    throw new IllegalStateException("gateway 必须排在 dev、test 或 prod 之后");
                }
            }
        };
    }
}
