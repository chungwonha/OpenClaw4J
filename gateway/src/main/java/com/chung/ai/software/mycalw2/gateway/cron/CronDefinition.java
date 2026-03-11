package com.chung.ai.software.mycalw2.gateway.cron;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Definition of a registered cron job.
 *
 * When the cron fires (based on cronExpression), the gateway builds an agent prompt
 * from promptTemplate and routes it to the configured agent/session.
 */
@Data
@Builder
public class CronDefinition {

    private final String id;
    private final String name;
    private final String description;

    /** Name of the registered agent to route to. Defaults to "default". */
    @Builder.Default
    private final String agentName = "default";

    /**
     * Session ID to use. If null, a dedicated session "cron:{id}" is auto-created.
     */
    private final String sessionId;

    /**
     * Spring cron expression (6 fields: second minute hour day-of-month month day-of-week).
     * Examples:
     *   "0 * * * * *"     — every minute
     *   "0 0 9 * * MON-FRI" — 9am on weekdays
     */
    private final String cronExpression;

    /**
     * Prompt template. Supported placeholders:
     *   {{cronName}}  — this job's name
     *   {{timestamp}} — ISO-8601 timestamp when the job fired
     */
    @Builder.Default
    private final String promptTemplate = "A scheduled task has fired. Task: {{cronName}}\nTimestamp: {{timestamp}}";

    /** Where to send the agent's response. */
    @Builder.Default
    private final OutputTarget outputTarget = OutputTarget.LOG;

    /**
     * HTTP URL to POST the result to.
     * Required when outputTarget == REPLY_URL; ignored otherwise.
     */
    private final String replyUrl;

    /** Whether this job is currently active. Volatile so enable/disable is visible across threads. */
    @Builder.Default
    private volatile boolean enabled = true;

    @Builder.Default
    private final Instant registeredAt = Instant.now();

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
