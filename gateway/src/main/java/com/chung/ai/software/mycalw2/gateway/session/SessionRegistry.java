package com.chung.ai.software.mycalw2.gateway.session;

import com.chung.ai.software.mycalw2.ChatAgentFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a map of active {@link AgentSession}s keyed by Teams conversation ID.
 *
 * Sessions are created on demand — if a message arrives for a conversation that
 * has no session yet, a fresh one is created via {@link ChatAgentFactory}, giving
 * it an isolated memory window and the full set of tools from the core module.
 * This models OpenClaw's "per-channel isolated session" behaviour.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SessionRegistry {

    /** Provided by CoreAgentConfig — creates ChatAgents with tools + memory. */
    private final ChatAgentFactory agentFactory;

    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    /**
     * Returns the existing session for {@code conversationId}, or creates a new one
     * if none exists yet.
     */
    public AgentSession getOrCreate(String conversationId) {
        return sessions.computeIfAbsent(conversationId, id -> {
            log.info("[SessionRegistry] New session created for conversationId='{}'", id);
            return new AgentSession(id, agentFactory);
        });
    }

    public AgentSession get(String conversationId) {
        return sessions.get(conversationId);
    }

    public Collection<AgentSession> all() {
        return sessions.values();
    }

    public int count() {
        return sessions.size();
    }
}
