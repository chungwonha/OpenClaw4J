package com.chung.ai.software.mycalw2.gateway.webhook;

import lombok.Builder;
import lombok.Data;

/**
 * Metadata carried in {@link com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEvent#getMetadata()}
 * for WEBHOOK events.
 *
 * Bundles the resolved definition together with the raw incoming payload so
 * that {@link com.chung.ai.software.mycalw2.gateway.dispatcher.AgentDispatcher}
 * does not need to re-query the registry during dispatch.
 */
@Data
@Builder
public class WebhookContext {
    private final WebhookDefinition definition;
    private final String rawPayload;
}
