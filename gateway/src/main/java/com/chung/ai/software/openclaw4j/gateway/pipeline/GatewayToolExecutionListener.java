package com.chung.ai.software.openclaw4j.gateway.pipeline;

import com.chung.ai.software.openclaw4j.gateway.hook.HookEventType;
import com.chung.ai.software.openclaw4j.gateway.hook.HookExecutor;
import com.chung.ai.software.openclaw4j.tool.ToolExecutionListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class GatewayToolExecutionListener implements ToolExecutionListener {

    private final HookExecutor hookExecutor;

    @Override
    public void beforeToolExecution(String toolName, String toolInput, String sessionId) {
        log.debug("[Pipeline] TOOL_BEFORE tool='{}' session='{}'", toolName, sessionId);
        hookExecutor.fire(HookEventType.TOOL_BEFORE, sessionId, toolName,
                Map.of("toolName", toolName, "toolInput", toolInput != null ? toolInput : ""));
    }

    @Override
    public void afterToolExecution(String toolName, String toolInput, String toolOutput, String sessionId) {
        log.debug("[Pipeline] TOOL_AFTER tool='{}' session='{}'", toolName, sessionId);
        hookExecutor.fire(HookEventType.TOOL_AFTER, sessionId, toolName,
                Map.of("toolName", toolName, "toolInput", toolInput != null ? toolInput : "",
                       "toolOutput", toolOutput != null ? toolOutput : ""));
    }
}
