package com.chung.ai.software.mycalw2.gateway.webhook;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Immutable definition of a registered webhook endpoint.
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
public class WebhookDefinition {

    private final String id;
    private final String name;
    private final String description;

    /** Name of the registered agent to route to. Defaults to "default". */
    @Builder.Default
    private final String agentName = "default";

    /**
     * Session ID to use for this webhook. If null, a dedicated session
     * "webhook:{id}" is auto-created, giving each webhook its own
     * isolated conversation memory.
     */
    private final String sessionId;

    /**
     * Jinja-style template for the agent prompt.
     * Supported placeholders:
     *   {{payload}}      — the raw request body
     *   {{webhookName}}  — this webhook's name
     *   {{timestamp}}    — ISO-8601 timestamp of the event
     */
    @Builder.Default
    private final String promptTemplate = "A webhook event was received. Process this payload:\n{{payload}}";

    /** Where to send the agent's response after processing. */
    @Builder.Default
    private final OutputTarget outputTarget = OutputTarget.LOG;

    /**
     * HTTP URL to POST the result to.
     * Required when outputTarget == REPLY_URL; ignored otherwise.
     */
    private final String replyUrl;

    @Builder.Default
    private final Instant registeredAt = Instant.now();

    public enum OutputTarget {
        /** Discard the result silently. Use for fire-and-forget. */
        NONE,
        /** Log the result at INFO level. Default — safe for development. */
        LOG,
        /** HTTP POST the result as JSON to replyUrl. */
        REPLY_URL
    }
}
