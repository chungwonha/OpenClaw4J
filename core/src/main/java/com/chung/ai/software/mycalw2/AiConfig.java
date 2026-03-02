package com.chung.ai.software.mycalw2;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
//import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@Data
@Slf4j
public class AiConfig {

    @Value("${openai.api-key:dummy}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${openai.timeout:60s}") // Default to 60 seconds if not specified
    private Duration timeout;

    @Value("${TAVILY_API_KEY:dummy}")
    private String tavilyApiKey;

    @Value("${app.ai.log}")
    private boolean logSwitch;

    @Value("${app.ai.local}")
    private boolean local;

    @Value("${app.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${app.ai.ollama.model}")
    private String ollamaModel;

    @Bean
    public ChatLanguageModel chatModel() {
//        log.info("Initializing ChatModel....");
        if ("demo".equals(openAiApiKey)
            || "dummy".equals(openAiApiKey)
            || openAiApiKey == null
            || openAiApiKey.isEmpty()
            || openAiApiKey.startsWith("${")) {
            // Return a simple mock model so Spring always has a usable ChatModel bean.
            // This keeps `@SpringBootTest` integration tests deterministic without requiring real API keys.

            return new ChatLanguageModel() {
                @Override
                public ChatResponse chat(ChatRequest chatRequest) {
                    return ChatResponse.builder()
                        .aiMessage(AiMessage.from("This is a mock response from the AI."))
                        .build();
                }

                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages) {
                    return Response.from(AiMessage.from("This is a mock response from the AI."));
                }
            };
        }
        if(local){
            log.info("Using Ollama model: {}", ollamaModel);
            log.info("Ollama base URL: {}", ollamaBaseUrl);
            return OllamaChatModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(this.ollamaModel)
                    .build();
        }else {
            return OpenAiChatModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName(openAiModel)
                    .logRequests(logSwitch)
                    .logResponses(logSwitch)
                    .timeout(timeout)
                    .build();
        }
    }

    @Bean
    public StreamingChatLanguageModel streamingChatModel(){
        return OpenAiStreamingChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(openAiModel)
                .timeout(timeout)
                .build();
    }

    @Bean
    public WebSearchContentRetriever webSearchContentRetriever(){
        // Let's create our web search content retriever.
        WebSearchEngine webSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(tavilyApiKey) // get a free key: https://app.tavily.com/sign-in
                .build();

        return WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .maxResults(12)
                .build();
    }


//    @Bean
//    ToolProvider toolProvider() {
//
//        McpTransport transport = new HttpMcpTransport.Builder()
//                .sseUrl("https://mcp.tavily.com/mcp/?tavilyApiKey="+tavilyApiKey)
//                .timeout(Duration.ofSeconds(60))
//                .logRequests(true)
//                .logResponses(true)
//                .build();
//
//        McpClient mcpClient = new DefaultMcpClient.Builder()
//                .transport(transport)
//                .build();
//
//        return McpToolProvider.builder()
//                .mcpClients(List.of(mcpClient))
//                .build();
//    }
}
