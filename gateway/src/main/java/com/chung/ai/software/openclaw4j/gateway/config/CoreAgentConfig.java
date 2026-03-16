package com.chung.ai.software.openclaw4j.gateway.config;

import com.chung.ai.software.openclaw4j.AgentPersistenceService;
import com.chung.ai.software.openclaw4j.ChatAgentFactory;
import com.chung.ai.software.openclaw4j.tools.CommandLineTool;
import com.chung.ai.software.openclaw4j.tools.FileManagementTool;
import com.chung.ai.software.openclaw4j.tools.HttpRequestTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Bridges the core module's agent infrastructure into the gateway's Spring context.
 *
 * WHY manual @Bean declarations instead of @ComponentScan of the core package:
 *
 *   GatewayApplication scans only com.chung.ai.software.openclaw4j.gateway.* so core's
 *   @Service/@Component classes are NOT picked up automatically.  That is intentional —
 *   it prevents ChatCommandLineRunner (which blocks waiting for console input) and
 *   AiConfig (which would conflict with GatewayAiConfig) from loading into the gateway.
 *
 *   By declaring exactly the beans we need here, we get full agent + tool capabilities
 *   with zero risk of unwanted side-effects from core's other beans.
 *
 * What each bean gives the gateway:
 *   FileManagementTool  — lets agents read/write files on the host machine
 *   HttpRequestTool     — lets agents make outbound HTTP GET/POST/PUT/DELETE calls
 *   CommandLineTool     — lets agents run OS commands (use with care in production)
 *   AgentPersistenceService — saves agent metadata as markdown files under agents/
 *   ChatAgentFactory    — creates a fully-equipped ChatAgent (memory + tools + MCP)
 *                         for each new Teams conversation session
 */
@Configuration
@Slf4j
public class CoreAgentConfig {

    @Bean
    public FileManagementTool fileManagementTool() {
        return new FileManagementTool();
    }

    @Bean
    public HttpRequestTool httpRequestTool() {
        return new HttpRequestTool();
    }

    @Bean
    public CommandLineTool commandLineTool() {
        return new CommandLineTool();
    }

    @Bean
    public AgentPersistenceService agentPersistenceService() {
        return new AgentPersistenceService();
    }

    @Bean
    public TaskScheduler cronTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("cron-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * ChatAgentFactory uses the ChatLanguageModel bean from GatewayAiConfig
     * (OpenAI / Ollama / Mock depending on what credentials are configured).
     *
     * Each call to chatAgentFactory.createChatAgent(name, description) produces
     * an independent ChatAgent with its own 200-message memory window, all three
     * tools, and an empty MCP registry ready to be populated at runtime.
     */
    @Bean
    public ChatAgentFactory chatAgentFactory(ChatLanguageModel chatModel,
                                             FileManagementTool fileManagementTool,
                                             HttpRequestTool httpRequestTool,
                                             CommandLineTool commandLineTool,
                                             AgentPersistenceService agentPersistenceService) {
        log.info("[CoreAgent] ChatAgentFactory ready — agents will have file, HTTP, and CLI tools");
        return new ChatAgentFactory(chatModel, fileManagementTool, httpRequestTool,
                commandLineTool, agentPersistenceService);
    }
}
