package com.chung.ai.software.openclaw4j.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Gateway service.
 *
 * The gateway is a long-running process that:
 *  - Accepts inbound connections from messaging clients (MS Teams for the MVP)
 *  - Normalises all inputs into GatewayEvents and queues them
 *  - Dispatches events to per-session AI agents asynchronously
 *  - Fires time-based heartbeat events to enable proactive agent behaviour
 *
 * Architecture is inspired by OpenClaw:
 *   inputs (messages, heartbeats, cron, hooks, webhooks)
 *     → EventQueue
 *       → AgentDispatcher
 *         → AgentSession (per conversation, isolated memory)
 *           → reply via client-specific channel (Teams Bot Framework API)
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
