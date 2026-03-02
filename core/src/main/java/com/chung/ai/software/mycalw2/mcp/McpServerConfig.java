package com.chung.ai.software.mycalw2.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {
    private String name;
    private String transport; // "stdio" or "sse"
    private List<String> command; // for stdio
    private String url; // for sse
    private Map<String, String> env; // for stdio
    private Map<String, String> headers; // for sse
}
