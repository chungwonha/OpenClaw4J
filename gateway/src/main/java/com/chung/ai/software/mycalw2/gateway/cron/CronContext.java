package com.chung.ai.software.mycalw2.gateway.cron;

import lombok.Builder;
import lombok.Data;

/**
 * Metadata attached to a CRON GatewayEvent.
 * Carries the CronDefinition so AgentDispatcher can build the prompt and route the result.
 */
@Data
@Builder
public class CronContext {
    private final CronDefinition definition;
}
