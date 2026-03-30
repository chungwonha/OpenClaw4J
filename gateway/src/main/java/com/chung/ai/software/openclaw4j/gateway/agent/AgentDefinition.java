package com.chung.ai.software.openclaw4j.gateway.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Describes a named agent that can be registered in the gateway.
 *
 * A definition is a template — it carries the agent's identity and purpose.
 * The actual {@link com.chung.ai.software.openclaw4j.ChatAgent} instances are
 * created per-session by AgentSession, so each conversation gets isolated memory
 * even when two sessions use the same named agent.
 *
 * Fields
 * ------
 * name        Unique identifier used for routing.
 *             Referenced with /use name  or  @name message  in Teams chat.
 * description Passed to ChatAgentFactory as the agent's role / persona.
 *             The LLM uses it as part of the system context.
 * registeredAt Timestamp for audit / listing purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentDefinition {

    /** Unique, URL-safe name (e.g. "default", "research", "calendar"). */
    private String name;

    /** Human-readable role / persona — becomes the agent's system description. */
    private String description;

    @Builder.Default
    private Instant registeredAt = Instant.now();
}
