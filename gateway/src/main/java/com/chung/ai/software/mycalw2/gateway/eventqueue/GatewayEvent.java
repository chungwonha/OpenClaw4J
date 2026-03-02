package com.chung.ai.software.mycalw2.gateway.eventqueue;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * A single unit of work flowing through the gateway.
 * Every input type (message, heartbeat, cron, hook, webhook) is
 * normalised into a GatewayEvent before entering the queue.
 */
@Data
@Builder
public class GatewayEvent {

    /** Identifies which kind of input triggered this event. */
    private final GatewayEventType type;

    /**
     * The conversation / session this event belongs to.
     * For broadcast events (heartbeat) this is set to {@code "*"}.
     */
    private final String sessionId;

    /**
     * The human-readable text payload — the message text for MESSAGE events,
     * the cron prompt for CRON events, etc.
     */
    private final String payload;

    /**
     * Channel-specific metadata.
     * For Teams MESSAGE events this holds the original {@code TeamsActivity}.
     */
    private final Object metadata;

    private final Instant timestamp;
}
