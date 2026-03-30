package com.chung.ai.software.openclaw4j.gateway.agent;

import lombok.Builder;
import lombok.Data;

/**
 * Metadata for an AGENT_MESSAGE event — one agent delegating a task to another.
 */
@Data
@Builder
public class AgentMessageContext {
    /** Session where the result should be delivered. */
    private final String replySessionId;
    /** Name of the target agent to handle the task. */
    private final String targetAgentName;
    /** Name of the originating agent (for context). */
    private final String fromAgentName;
}
