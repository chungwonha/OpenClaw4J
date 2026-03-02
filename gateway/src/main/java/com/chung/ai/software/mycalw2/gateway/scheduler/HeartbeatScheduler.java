package com.chung.ai.software.mycalw2.gateway.scheduler;

import com.chung.ai.software.mycalw2.gateway.eventqueue.EventQueue;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEvent;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Time-based input source — the OpenClaw "heartbeat".
 *
 * Fires on a configurable interval (default: 30 minutes) and pushes a
 * HEARTBEAT event into the queue.  The AgentDispatcher handles it, which
 * lets the agent run proactive checks (inbox, calendar, tasks) even when
 * no human message has arrived.
 *
 * This is the mechanism that makes the system feel "alive" — the agent
 * keeps doing things without being explicitly asked each time.
 *
 * Enabled/disabled via {@code gateway.heartbeat.enabled} (default: true).
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gateway.heartbeat.enabled", havingValue = "true", matchIfMissing = true)
public class HeartbeatScheduler {

    private final EventQueue eventQueue;

    /**
     * Interval controlled by {@code gateway.heartbeat.interval-ms}.
     * Default: 1 800 000 ms = 30 minutes.
     */
    @Scheduled(fixedDelayString = "${gateway.heartbeat.interval-ms:1800000}")
    public void tick() {
        log.info("[Heartbeat] Tick at {}", Instant.now());

        GatewayEvent heartbeat = GatewayEvent.builder()
                .type(GatewayEventType.HEARTBEAT)
                .sessionId("*")        // broadcast — the dispatcher decides who to notify
                .payload("heartbeat")
                .timestamp(Instant.now())
                .build();

        eventQueue.enqueue(heartbeat);
    }
}
