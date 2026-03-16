package com.chung.ai.software.openclaw4j;

import com.chung.ai.software.openclaw4j.mcp.McpRegistry;
import com.chung.ai.software.openclaw4j.mcp.McpServerConfig;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Profile("!test")
@Slf4j
public class ChatCommandLineRunner implements CommandLineRunner {

    private final ChatAgentFactory chatAgentFactory;
    private final AgentPersistenceService persistenceService;

    public ChatCommandLineRunner(ChatAgentFactory chatAgentFactory, AgentPersistenceService persistenceService) {
        this.chatAgentFactory = chatAgentFactory;
        this.persistenceService = persistenceService;
    }

    @Override
    public void run(String... args) {
        log.info("ChatCommandLineRunner started");
        List<ChatAgent> agents = new ArrayList<>();
        
        List<AgentMetadata> existingAgents = persistenceService.loadAllAgents();
        if (existingAgents.isEmpty()) {
            agents.add(chatAgentFactory.createChatAgent("Default Agent", "Initial default agent"));
        } else {
            for (AgentMetadata metadata : existingAgents) {
                agents.add(chatAgentFactory.createChatAgent(metadata));
            }
        }
        
        int activeAgentIndex = 0;

        Scanner scanner = new Scanner(System.in);
        System.out.println("--- AI Chat Bot (Type 'exit' to quit, '/agents' to start a new agent) ---");
        System.out.println("Commands: '/agents', '/list', '/use <index>', '/mcp <command> <args...>'");

        while (true) {
            ChatAgent activeAgent = agents.get(activeAgentIndex);
            System.out.print("[" + activeAgent.name() + "] (" + activeAgentIndex + ") > ");
            String input = scanner.nextLine();

            if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                System.out.println("Goodbye!");
                break;
            }

            String trimmedInput = input.trim();
            if (trimmedInput.equalsIgnoreCase("/agents")) {
                System.out.print("Enter agent name: ");
                String name = scanner.nextLine().trim();
                System.out.print("Enter agent description: ");
                String description = scanner.nextLine().trim();
                
                ChatAgent newAgent = chatAgentFactory.createChatAgent(name, description);
                agents.add(newAgent);
                activeAgentIndex = agents.size() - 1;
                
                // Save the new agent immediately
                persistenceService.saveAgent(AgentMetadata.builder()
                        .name(name)
                        .description(description)
                        .mcpServers(new ArrayList<>())
                        .build());

                System.out.println("--- New agent '" + name + "' created. Active agent: [" + activeAgentIndex + "] ---");
                continue;
            }

            if (trimmedInput.equalsIgnoreCase("/list")) {
                System.out.println("--- Tracked Agents ---");
                for (int i = 0; i < agents.size(); i++) {
                    ChatAgent agent = agents.get(i);
                    System.out.println("Agent [" + i + "]: " + agent.name() + (i == activeAgentIndex ? " (Active)" : ""));
                    System.out.println("  Desc: " + agent.description());
                }
                continue;
            }

            if (trimmedInput.toLowerCase().startsWith("/use ")) {
                try {
                    int index = Integer.parseInt(trimmedInput.substring(5).trim());
                    if (index >= 0 && index < agents.size()) {
                        activeAgentIndex = index;
                        System.out.println("--- Switched to Agent [" + activeAgentIndex + "] ---");
                    } else {
                        System.out.println("Error: Invalid agent index.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error: Please provide a valid number for /use.");
                }
                continue;
            }

            if (trimmedInput.toLowerCase().startsWith("/mcp ")) {
                handleMcpCommand(trimmedInput.substring(5).trim(), agents.get(activeAgentIndex));
                continue;
            }

            if (trimmedInput.isEmpty()) {
                continue;
            }

            try {
                ChatAgent chatAgent = agents.get(activeAgentIndex);
                String response = chatAgent.chat(input);
                System.out.println("AI: " + response);
            } catch (Exception e) {
                log.error("Error communicating with AI: {}", e.getMessage());
                System.out.println("AI: Sorry, something went wrong. Check the logs for details.");
            }
        }
    }

    private void handleMcpCommand(String mcpCommand, ChatAgent activeAgent) {
        if (mcpCommand.isEmpty()) {
            System.out.println("Usage: /mcp <add|remove|list> [args...]");
            return;
        }

        String[] parts = mcpCommand.split("\\s+");
        String action = parts[0].toLowerCase();

        switch (action) {
            case "add":
                handleAddMcp(parts, activeAgent);
                break;
            case "remove":
                handleRemoveMcp(parts, activeAgent);
                break;
            case "list":
                handleListMcp(activeAgent);
                break;
            default:
                System.out.println("Unknown MCP action: " + action);
        }
    }

    private void handleAddMcp(String[] parts, ChatAgent activeAgent) {
        String name = null;
        String transport = null;
        List<String> command = new ArrayList<>();
        String url = null;
        Map<String, String> env = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        for (int i = 1; i < parts.length; i++) {
            switch (parts[i]) {
                case "--name":
                    if (i + 1 < parts.length) name = parts[++i];
                    break;
                case "--stdio":
                    transport = "stdio";
                    // Capture everything after --stdio as command
                    for (int j = i + 1; j < parts.length; j++) {
                        if (parts[j].startsWith("--") && !parts[j].equals("--dir")) break; // simplistic flag detection
                        command.add(parts[j]);
                        i = j;
                    }
                    break;
                case "--http":
                    transport = "sse";
                    break;
                case "--sseUrl":
                    if (i + 1 < parts.length) url = parts[++i];
                    break;
                case "--header":
                    if (i + 1 < parts.length) {
                        String[] kv = parts[++i].split("=", 2);
                        if (kv.length == 2) headers.put(kv[0], kv[1]);
                    }
                    break;
                case "--dir":
                    // Handle --dir as part of command for stdio if needed, or special env
                    if (i + 1 < parts.length) {
                        command.add("--dir");
                        command.add(parts[++i]);
                    }
                    break;
            }
        }

        if (name == null || transport == null) {
            System.out.println("Error: Missing --name or transport (--stdio|--http)");
            return;
        }

        McpServerConfig config = McpServerConfig.builder()
                .name(name)
                .transport(transport)
                .command(command)
                .url(url)
                .env(env)
                .headers(headers)
                .build();

        try {
            activeAgent.mcpRegistry().add(config);
            System.out.println("--- MCP server '" + name + "' added to active agent registry ---");
            System.out.println("NOTE: Dynamic ToolProvider update is currently not supported in this LangChain4j version (1.0.0-beta1).");
        } catch (Exception e) {
            System.out.println("Error adding MCP server: " + e.getMessage());
            log.error("Error adding MCP server", e);
        }
    }

    private McpClient createMcpClient(McpServerConfig config) {
        McpTransport transport;
        if ("stdio".equals(config.getTransport())) {
            transport = new StdioMcpTransport.Builder()
                    .command(config.getCommand())
                    .environment(config.getEnv())
                    .build();
        } else {
            transport = new HttpMcpTransport.Builder()
                    .sseUrl(config.getUrl())
                    .build();
        }

        return new DefaultMcpClient.Builder()
                .transport(transport)
                .build();
    }

    private void handleRemoveMcp(String[] parts, ChatAgent activeAgent) {
        String name = null;
        for (int i = 1; i < parts.length; i++) {
            if ("--name".equals(parts[i]) && i + 1 < parts.length) {
                name = parts[i + 1];
                break;
            }
        }

        if (name == null) {
            System.out.println("Error: Missing --name");
            return;
        }

        activeAgent.mcpRegistry().remove(name);
        System.out.println("--- MCP server '" + name + "' removed from registry ---");
    }

    private void handleListMcp(ChatAgent activeAgent) {
        List<McpServerConfig> servers = activeAgent.mcpRegistry().list();
        if (servers.isEmpty()) {
            System.out.println("No MCP servers configured for this agent.");
        } else {
            System.out.println("--- Configured MCP Servers ---");
            for (McpServerConfig s : servers) {
                System.out.println("- " + s.getName() + " (" + s.getTransport() + ")");
            }
        }
    }
}
