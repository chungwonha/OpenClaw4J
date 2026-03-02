package com.chung.ai.software.mycalw2.gateway.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AI model configuration for the gateway.
 *
 * Priority order:
 *   1. Ollama (local)  — when {@code app.ai.local=true}
 *   2. OpenAI          — when {@code OPENAI_API_KEY} is set
 *   3. Mock model      — fallback for local dev / tests
 */
@Configuration
@Slf4j
public class GatewayAiConfig {

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${openai.timeout:60s}")
    private Duration timeout;

    @Value("${app.ai.log:false}")
    private boolean logSwitch;

    @Value("${app.ai.local:false}")
    private boolean local;

    @Value("${app.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ai.ollama.model:llama3.3}")
    private String ollamaModel;

    @Bean
    public ChatLanguageModel chatModel() {
        if (local) {
            log.info("[AI] Using Ollama model='{}' at {}", ollamaModel, ollamaBaseUrl);
            return OllamaChatModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(ollamaModel)
                    .build();
        }

        boolean hasKey = openAiApiKey != null
                && !openAiApiKey.isBlank()
                && !openAiApiKey.startsWith("${");

        if (hasKey) {
            log.info("[AI] Using OpenAI model='{}'", openAiModel);
            return OpenAiChatModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName(openAiModel)
                    .logRequests(logSwitch)
                    .logResponses(logSwitch)
                    .timeout(timeout)
                    .build();
        }

        log.warn("[AI] No AI credentials configured. Using mock model. " +
                 "Set OPENAI_API_KEY or app.ai.local=true to use a real model.");
        return new MockChatLanguageModel();
    }

    /**
     * Executor used by the AgentDispatcher to process events off the scheduler thread.
     * Uses a cached thread pool on Java 17; replace with
     * {@code Executors.newVirtualThreadPerTaskExecutor()} when upgrading to Java 21+.
     */
    @Bean(name = "gatewayExecutor")
    public Executor gatewayExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("gateway-event-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    // ------------------------------------------------------------------ //

    private static class MockChatLanguageModel implements ChatLanguageModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(
                            "[Mock AI] No model configured. " +
                            "Set OPENAI_API_KEY or enable Ollama to get real responses."))
                    .build();
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return Response.from(AiMessage.from("[Mock AI] No model configured."));
        }
    }
}
