package com.chung.ai.software.mycalw2;

import com.chung.ai.software.mycalw2.mcp.McpServerConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class AgentPersistenceService {
    private static final String AGENTS_DIR = "agents";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public AgentPersistenceService() {
        new File(AGENTS_DIR).mkdirs();
    }

    public void saveAgent(AgentMetadata metadata) {
        String filename = sanitizeFilename(metadata.getName()) + ".md";
        Path path = Path.of(AGENTS_DIR, filename);

        StringBuilder sb = new StringBuilder();
        sb.append("# Agent: ").append(metadata.getName()).append("\n\n");
        sb.append("## Description\n").append(metadata.getDescription()).append("\n\n");
        sb.append("## MCP Servers\n");
        
        try {
            String json = MAPPER.writeValueAsString(metadata.getMcpServers());
            sb.append("```json\n").append(json).append("\n```\n");
            Files.writeString(path, sb.toString());
            log.info("Saved agent metadata to {}", path);
        } catch (IOException e) {
            log.error("Failed to save agent metadata", e);
        }
    }

    public List<AgentMetadata> loadAllAgents() {
        List<AgentMetadata> agents = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Path.of(AGENTS_DIR))) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        AgentMetadata metadata = loadAgent(p);
                        if (metadata != null) {
                            agents.add(metadata);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list agents directory", e);
        }
        return agents;
    }

    private AgentMetadata loadAgent(Path path) {
        try {
            String content = Files.readString(path);
            String name = extractSection(content, "# Agent: (.*)");
            String description = extractSection(content, "## Description\\n([\\s\\S]*?)\\n\\n## MCP Servers");
            String jsonMcp = extractSection(content, "```json\\n([\\s\\S]*?)\\n```");

            List<McpServerConfig> mcpServers = new ArrayList<>();
            if (jsonMcp != null) {
                try {
                    mcpServers = MAPPER.readValue(jsonMcp, MAPPER.getTypeFactory().constructCollectionType(List.class, McpServerConfig.class));
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse MCP servers JSON from {}", path, e);
                }
            }

            return AgentMetadata.builder()
                    .name(name != null ? name.trim() : "Unknown")
                    .description(description != null ? description.trim() : "")
                    .mcpServers(mcpServers)
                    .build();
        } catch (IOException e) {
            log.error("Failed to read agent file {}", path, e);
            return null;
        }
    }

    private String extractSection(String content, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }
}
