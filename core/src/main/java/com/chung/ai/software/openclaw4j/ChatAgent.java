package com.chung.ai.software.openclaw4j;


import com.chung.ai.software.openclaw4j.mcp.McpRegistry;
import com.chung.ai.software.openclaw4j.mcp.McpServerConfig;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class ChatAgent {

    private final ChatAgentService service;
    private final McpToolProvider mcpToolProvider;
    private final McpRegistry mcpRegistry;
    private final String name;
    private final String description;

    public ChatAgent(String name, String description, ChatAgentService service, McpToolProvider mcpToolProvider, McpRegistry mcpRegistry) {
        this.name = name;
        this.description = description;
        this.service = service;
        this.mcpToolProvider = mcpToolProvider;
        this.mcpRegistry = mcpRegistry;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String chat(String prompts) {
        return service.chat(prompts);
    }

    public McpToolProvider mcpToolProvider() {
        return mcpToolProvider;
    }

    public McpRegistry mcpRegistry() {
        return mcpRegistry;
    }

    public void refreshMcpTools() {
        // Dynamic refresh is currently not supported by the available McpToolProvider API in version 1.0.0-beta1.
        // We'll keep the registry updated and we can potentially recreate the agent or its tool provider if needed.
    }

    public McpClient createMcpClient(McpServerConfig config) {
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

    public interface ChatAgentService {
        @UserMessage("Chat and Response to prompts from users in concise manner." +
                "Prompts: {{prompts}}")
        String chat(@V("prompts") String prompts);
    }
}
