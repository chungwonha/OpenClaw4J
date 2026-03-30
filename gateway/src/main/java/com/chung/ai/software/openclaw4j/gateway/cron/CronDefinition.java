package com.chung.ai.software.openclaw4j.gateway.cron;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Definition of a registered cron job.
 *
 * When the cron fires (based on cronExpression), the gateway builds an agent prompt
 * from promptTemplate and routes it to the configured agent/session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CronDefinition {

    private String id;
    private String name;
    private String description;

    /** Name of the registered agent to route to. Defaults to "default". */
    @Builder.Default
    private String agentName = "default";

    /**
     * Session ID to use. If null, a dedicated session "cron:{id}" is auto-created.
     */
    private String sessionId;

    /**
     * Spring cron expression (6 fields: second minute hour day-of-month month day-of-week).
     * Examples:
     *   "0 * * * * *"     — every minute
     *   "0 0 9 * * MON-FRI" — 9am on weekdays
     */
    private String cronExpression;

    /**
     * Prompt template. Supported placeholders:
     *   {{cronName}}  — this job's name
     *   {{timestamp}} — ISO-8601 timestamp when the job fired
     */
    @Builder.Default
    private String promptTemplate = "A scheduled task has fired. Task: {{cronName}}\nTimestamp: {{timestamp}}";

    /** Where to send the agent's response. */
    @Builder.Default
    private OutputTarget outputTarget = OutputTarget.LOG;

    /**
     * HTTP URL to POST the result to.
     * Required when outputTarget == REPLY_URL; ignored otherwise.
     */
    private String replyUrl;

    /** Whether this job is currently active. Volatile so enable/disable is visible across threads. */
    @Builder.Default
    private volatile boolean enabled = true;

    @Builder.Default
    private Instant registeredAt = Instant.now();

    public enum OutputTarget {
        /** Discard the result silently. */
        NONE,
        /** Log the result at INFO level. Default. */
        LOG,
        /** HTTP POST the result as JSON to replyUrl. */
        REPLY_URL
    }

    // Explicit setter needed because Lombok @Data won't generate one for volatile fields with @Builder
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
