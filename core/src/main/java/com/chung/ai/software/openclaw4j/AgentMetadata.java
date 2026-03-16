package com.chung.ai.software.openclaw4j;

import com.chung.ai.software.openclaw4j.mcp.McpServerConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentMetadata {
    private String name;
    private String description;
    @Builder.Default
    private List<McpServerConfig> mcpServers = new ArrayList<>();
}
