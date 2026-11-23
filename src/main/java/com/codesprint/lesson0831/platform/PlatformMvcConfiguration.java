package com.codesprint.lesson0831.platform;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration(proxyBeanMethods=false)
@Profile("platform")
public class PlatformMvcConfiguration implements WebMvcConfigurer {
    private final ThreadPoolTaskExecutor executor;
    public PlatformMvcConfiguration(){executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(4);executor.setMaxPoolSize(8);executor.setQueueCapacity(16);executor.setThreadNamePrefix("platform-stream-");executor.setWaitForTasksToCompleteOnShutdown(false);executor.initialize();}
    @Bean public ThreadPoolTaskExecutor platformStreamExecutor(){return executor;}
    @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer){configurer.setTaskExecutor(executor);configurer.setDefaultTimeout(100000);}
}
