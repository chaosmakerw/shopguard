package com.codesprint.lesson0831.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DemoProfileIsolationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(DemoConfiguration.class, DemoController.class, DemoWorkbenchController.class, DemoExceptionHandler.class);

    @Test
    void demoComponentsAreDisabledWithoutDemoProfile() {
        context.withPropertyValues("spring.profiles.active=dev").run(application -> {
            assertThat(application).doesNotHaveBean(DemoConfiguration.class)
                    .doesNotHaveBean(DemoController.class).doesNotHaveBean(DemoWorkbenchController.class).doesNotHaveBean(DemoExceptionHandler.class);
        });
    }

    @Test
    void productionCannotEnableDemoComponentsEvenWithDemoFlag() {
        context.withPropertyValues("spring.profiles.active=prod,demo,dev").run(application -> {
            assertThat(application).doesNotHaveBean(DemoConfiguration.class)
                    .doesNotHaveBean(DemoController.class).doesNotHaveBean(DemoWorkbenchController.class).doesNotHaveBean(DemoExceptionHandler.class);
        });
    }
}
