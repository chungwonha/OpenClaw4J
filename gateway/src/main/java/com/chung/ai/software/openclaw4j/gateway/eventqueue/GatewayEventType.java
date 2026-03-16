package com.chung.ai.software.openclaw4j.gateway.eventqueue;

/**
 * Mirrors the five input types from the OpenClaw architecture:
 * human messages, timer heartbeats, scheduled cron jobs,
 * internal state hooks, and external webhooks.
 * Agents can also message other agents (AGENT_MESSAGE).
 */
public enum GatewayEventType {
    /** A human message arriving from a messaging client (e.g. MS Teams). */
    MESSAGE,
    /** Fired on a regular timer interval (every N minutes) to trigger proactive agent checks. */
    HEARTBEAT,
    /** A scheduled cron-style event with its own prompt payload. */
    CRON,
    /** An internal lifecycle hook (startup, shutdown, agent-reset, etc.). */
    HOOK,
    /** An inbound webhook from an external system (Jira, GitHub, email, etc.). */
    WEBHOOK,
    /** One agent sending a task to another agent. */
    AGENT_MESSAGE
}
