package com.chung.ai.software.mycalw2.gateway.eventqueue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Central in-memory event queue.
 *
 * All input adapters (Teams, heartbeat, cron, webhooks) push events here.
 * The AgentDispatcher drains and processes them asynchronously, preserving
 * the decoupling that makes the OpenClaw-style architecture work.
 */
@Component
@Slf4j
public class EventQueue {

    private final LinkedBlockingQueue<GatewayEvent> queue = new LinkedBlockingQueue<>();

    public void enqueue(GatewayEvent event) {
        queue.offer(event);
        log.debug("Enqueued {} event for session '{}'", event.getType(), event.getSessionId());
    }

    /** Non-blocking single poll; returns null if the queue is empty. */
    public GatewayEvent poll() {
        return queue.poll();
    }

    /** Drain everything currently in the queue in one shot. */
    public List<GatewayEvent> drainAll() {
        List<GatewayEvent> events = new ArrayList<>();
        queue.drainTo(events);
        return events;
    }

    public int size() {
        return queue.size();
    }
}
