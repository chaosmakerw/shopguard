package com.codesprint.lesson0831.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeProfileConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeProfileConfiguration.class);

    @Test
    void productionCannotMixDevelopmentSecurityOrDemo() {
        for (String profiles : new String[]{"prod,dev", "prod,test", "prod,demo"}) {
            context.withPropertyValues("spring.profiles.active=" + profiles).run(application -> {
                assertThat(application).hasFailed();
                assertThat(application.getStartupFailure()).hasMessageContaining("prod 不得");
            });
        }
    }

    @Test
    void standaloneDemoFailsFast() {
        context.withPropertyValues("spring.profiles.active=demo").run(application -> {
            assertThat(application).hasFailed();
            assertThat(application.getStartupFailure()).hasMessageContaining("demo 必须");
        });
    }

    @Test
    void intendedProfileCombinationsPass() {
        for (String profiles : new String[]{"dev,demo", "dev,demo,compose", "test", "prod"}) {
            context.withPropertyValues("spring.profiles.active=" + profiles)
                    .run(application -> assertThat(application).hasNotFailed());
        }
    }
}
