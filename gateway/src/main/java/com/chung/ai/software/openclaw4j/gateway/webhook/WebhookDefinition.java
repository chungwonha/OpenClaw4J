package com.chung.ai.software.openclaw4j.gateway.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Definition of a registered webhook endpoint.
 *
 * When an external system POSTs to /api/webhooks/{id}, the gateway looks up
 * the matching WebhookDefinition to know:
 *  - Which agent to invoke
 *  - Which session context to use (or auto-create a dedicated one)
 *  - How to turn the raw payload into an agent prompt
 *  - Where to send the agent's response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookDefinition {

    private String id;
    private String name;
    private String description;

    /** Name of the registered agent to route to. Defaults to "default". */
    @Builder.Default
    private String agentName = "default";

    /**
     * Session ID to use for this webhook. If null, a dedicated session
     * "webhook:{id}" is auto-created, giving each webhook its own
     * isolated conversation memory.
     */
    private String sessionId;

    /**
     * Jinja-style template for the agent prompt.
     * Supported placeholders:
     *   {{payload}}      — the raw request body
     *   {{webhookName}}  — this webhook's name
     *   {{timestamp}}    — ISO-8601 timestamp of the event
     */
    @Builder.Default
    private String promptTemplate = "A webhook event was received. Process this payload:\n{{payload}}";

    /** Where to send the agent's response after processing. */
    @Builder.Default
    private OutputTarget outputTarget = OutputTarget.LOG;

    /**
     * HTTP URL to POST the result to.
     * Required when outputTarget == REPLY_URL; ignored otherwise.
     */
    private String replyUrl;

    @Builder.Default
    private Instant registeredAt = Instant.now();

    public enum OutputTarget {
        /** Discard the result silently. Use for fire-and-forget. */
        NONE,
        /** Log the result at INFO level. Default — safe for development. */
        LOG,
        /** HTTP POST the result as JSON to replyUrl. */
        REPLY_URL
    }
}
