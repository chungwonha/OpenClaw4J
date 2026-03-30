package com.chung.ai.software.openclaw4j.gateway.heartbeat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Defines a proactive prompt to run against agent sessions on each heartbeat tick.
 *
 * When the heartbeat fires, for each active session the gateway runs the
 * promptTemplate against the configured agent. The result is delivered via outputTarget.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeartbeatDefinition {

    private String id;
    private String name;
    private String description;

    /** Agent to route to. Defaults to "default". */
    @Builder.Default
    private String agentName = "default";

    /**
     * Prompt template. Placeholders:
     *   {{sessionId}}  — the session being probed
     *   {{timestamp}}  — ISO-8601 current time
     */
    @Builder.Default
    private String promptTemplate = "You are running a proactive check. Timestamp: {{timestamp}}. Session: {{sessionId}}. Provide a brief status update or any relevant observations.";

    /**
     * Session filter. "all" = run against all sessions. Otherwise, exact session ID.
     */
    @Builder.Default
    private String sessionFilter = "all";

    @Builder.Default
    private OutputTarget outputTarget = OutputTarget.LOG;

    /** HTTP URL to POST results to. Required when outputTarget == REPLY_URL. */
    private String replyUrl;

    @Builder.Default
    private volatile boolean enabled = true;

    @Builder.Default
    private Instant registeredAt = Instant.now();

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public enum OutputTarget { NONE, LOG, REPLY_URL }
}
