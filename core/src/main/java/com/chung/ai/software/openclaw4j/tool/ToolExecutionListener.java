package com.chung.ai.software.openclaw4j.tool;

public interface ToolExecutionListener {
    void beforeToolExecution(String toolName, String toolInput, String sessionId);
    void afterToolExecution(String toolName, String toolInput, String toolOutput, String sessionId);
}
