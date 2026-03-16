package com.chung.ai.software.openclaw4j.gateway.session;

import com.chung.ai.software.openclaw4j.ChatAgentFactory;
import com.chung.ai.software.openclaw4j.gateway.agent.AgentRegistry;
import com.chung.ai.software.openclaw4j.gateway.hook.HookExecutor;
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
 * Each session also receives the {@link AgentRegistry} so it can resolve named
 * agents and lazy-create per-session {@link com.chung.ai.software.openclaw4j.ChatAgent}
 * instances on first use.
 *
 * This models OpenClaw's "per-channel isolated session" behaviour.
 *
 * Note: explicit constructor (not @RequiredArgsConstructor) is required so that
 * {@link HookExecutor} can be injected and forwarded to every new {@link AgentSession}.
 */
@Component
@Slf4j
public class SessionRegistry {

    /** Provided by CoreAgentConfig — creates ChatAgents with tools + memory. */
    private final ChatAgentFactory agentFactory;

    /** Central registry of named agent definitions — passed to every new session. */
    private final AgentRegistry agentRegistry;

    /** Executor forwarded to every new session for hook firing. */
    private final HookExecutor hookExecutor;

    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    public SessionRegistry(ChatAgentFactory agentFactory,
                           AgentRegistry agentRegistry,
                           HookExecutor hookExecutor) {
        this.agentFactory = agentFactory;
        this.agentRegistry = agentRegistry;
        this.hookExecutor = hookExecutor;
    }

    /**
     * Returns the existing session for {@code conversationId}, or creates a new one
     * if none exists yet.
     */
    public AgentSession getOrCreate(String conversationId) {
        return sessions.computeIfAbsent(conversationId, id -> {
            log.info("[SessionRegistry] New session created for conversationId='{}'", id);
            return new AgentSession(id, agentFactory, agentRegistry, hookExecutor);
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
