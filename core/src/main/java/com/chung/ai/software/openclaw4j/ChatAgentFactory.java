package com.chung.ai.software.openclaw4j;

import com.chung.ai.software.openclaw4j.tools.CommandLineTool;
import com.chung.ai.software.openclaw4j.tools.FileManagementTool;
import com.chung.ai.software.openclaw4j.tools.HttpRequestTool;
import com.chung.ai.software.openclaw4j.mcp.McpRegistry;
import com.chung.ai.software.openclaw4j.mcp.McpServerConfig;
import com.chung.ai.software.openclaw4j.tool.CompositeToolProvider;
import com.chung.ai.software.openclaw4j.tool.ToolExecutionListener;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.mcp.McpToolProvider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ChatAgentFactory {

    @Setter
    private ToolExecutionListener toolExecutionListener;

    private final ChatLanguageModel chatModel;
    private final FileManagementTool fileManagementTool;
    private final HttpRequestTool httpRequestTool;
    private final CommandLineTool commandLineTool;
    private final AgentPersistenceService persistenceService;

    public ChatAgentFactory(ChatLanguageModel chatModel,
                            FileManagementTool fileManagementTool,
                            HttpRequestTool httpRequestTool,
                            CommandLineTool commandLineTool,
                            AgentPersistenceService persistenceService) {
        log.info("ChatAgentFactory initialized");
        this.chatModel = chatModel;
        this.fileManagementTool = fileManagementTool;
        this.httpRequestTool = httpRequestTool;
        this.commandLineTool = commandLineTool;
        this.persistenceService = persistenceService;
    }

    public ChatAgent createChatAgent(String name, String description) {
        AgentMetadata metadata = AgentMetadata.builder()
                .name(name)
                .description(description)
                .mcpServers(new ArrayList<>())
                .build();
        return createChatAgentInternal(metadata, name);
    }

    public ChatAgent createChatAgent(AgentMetadata metadata) {
        return createChatAgentInternal(metadata, metadata.getName());
    }

    private ChatAgent createChatAgentInternal(AgentMetadata metadata, String sessionId) {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(200);

        McpRegistry mcpRegistry = new McpRegistry(metadata.getMcpServers(), updatedServers -> {
            metadata.setMcpServers(updatedServers);
            persistenceService.saveAgent(metadata);
        });
        
        List<McpClient> mcpClients = new ArrayList<>();
        for (McpServerConfig config : mcpRegistry.list()) {
            try {
                mcpClients.add(createMcpClient(config));
                log.info("Initialized MCP client: {}", config.getName());
            } catch (Exception e) {
                log.error("Failed to initialize MCP client: {}", config.getName(), e);
            }
        }

        McpToolProvider mcpToolProvider = McpToolProvider.builder()
                .mcpClients(mcpClients)
                .build();

        CompositeToolProvider compositeToolProvider = new CompositeToolProvider(List.of(mcpToolProvider));
        compositeToolProvider.addStaticTools(fileManagementTool);
        compositeToolProvider.addStaticTools(httpRequestTool);
        compositeToolProvider.addStaticTools(commandLineTool);
        compositeToolProvider.setSessionId(sessionId);
        compositeToolProvider.setToolExecutionListener(toolExecutionListener);

        ChatAgent.ChatAgentService service = AiServices.builder(ChatAgent.ChatAgentService.class)
                .chatMemory(chatMemory)
                .chatLanguageModel(chatModel)
                .toolProvider(compositeToolProvider)
                .build();

        return new ChatAgent(metadata.getName(), metadata.getDescription(), service, mcpToolProvider, mcpRegistry);
    }
    private McpClient createMcpClient(McpServerConfig config) {
        dev.langchain4j.mcp.client.transport.McpTransport transport;
        if ("stdio".equals(config.getTransport())) {
            transport = new dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport.Builder()
                    .command(config.getCommand())
                    .environment(config.getEnv())
                    .build();
        } else {
            transport = new dev.langchain4j.mcp.client.transport.http.HttpMcpTransport.Builder()
                    .sseUrl(config.getUrl())
                    .build();
        }

        return new dev.langchain4j.mcp.client.DefaultMcpClient.Builder()
                .transport(transport)
                .build();
    }
}
