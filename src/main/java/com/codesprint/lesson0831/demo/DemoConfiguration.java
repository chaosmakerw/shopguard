package com.codesprint.lesson0831.demo;

import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import com.codesprint.lesson0831.rag.answer.RagAnswerService;
import com.codesprint.lesson0831.rag.answer.RagPromptBuilder;
import com.codesprint.lesson0831.rag.ingestion.*;
import com.codesprint.lesson0831.rag.retrieval.*;
import com.codesprint.lesson0831.security.CurrentUserContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev & demo & !prod")
public class DemoConfiguration {
    @Bean
    @Primary
    public ChatModel demoChatModel() {
        return new DemoChatModel();
    }

    @Bean
    public TrustedRetrievalRequestFactory demoRetrievalRequestFactory(CurrentUserContext context) {
        return new TrustedRetrievalRequestFactory(context);
    }

    @Bean
    public DemoKnowledgeRuntime demoKnowledgeRuntime(AgentOperationMetrics metrics) {
        return new DemoKnowledgeRuntime(metrics);
    }

}
