package com.chung.ai.software.mycalw2;

import com.chung.ai.software.mycalw2.mcp.McpServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AgentPersistenceServiceTest {

    @Autowired
    private AgentPersistenceService persistenceService;

    @Test
    public void testSaveAndLoad() throws IOException {
        String name = "TestAgent";
        String description = "This is a test agent description.";
        List<McpServerConfig> mcpServers = List.of(
                McpServerConfig.builder()
                        .name("weather")
                        .transport("http")
                        .url("http://localhost:8080/mcp")
                        .build()
        );

        AgentMetadata metadata = AgentMetadata.builder()
                .name(name)
                .description(description)
                .mcpServers(mcpServers)
                .build();

        persistenceService.saveAgent(metadata);

        Path agentPath = Path.of("agents", "TestAgent.md");
        assertTrue(Files.exists(agentPath));

        String content = Files.readString(agentPath);
        assertTrue(content.contains("# Agent: TestAgent"));
        assertTrue(content.contains(description));
        assertTrue(content.contains("weather"));

        List<AgentMetadata> allAgents = persistenceService.loadAllAgents();
        AgentMetadata loaded = allAgents.stream()
                .filter(a -> a.getName().equals(name))
                .findFirst()
                .orElse(null);

        assertNotNull(loaded);
        assertEquals(name, loaded.getName());
        assertEquals(description, loaded.getDescription());
        assertEquals(1, loaded.getMcpServers().size());
        assertEquals("weather", loaded.getMcpServers().get(0).getName());

        // Cleanup
        Files.deleteIfExists(agentPath);
    }
}
