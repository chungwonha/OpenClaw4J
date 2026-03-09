package com.chung.ai.software.mycalw2.gateway.session;

import com.chung.ai.software.mycalw2.ChatAgent;
import com.chung.ai.software.mycalw2.ChatAgentFactory;
import com.chung.ai.software.mycalw2.gateway.agent.AgentDefinition;
import com.chung.ai.software.mycalw2.gateway.agent.AgentRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * One session = one Teams conversation (DM or channel thread).
 *
 * Multi-agent routing
 * -------------------
 * Each session tracks an "active agent" (defaults to "default").
 * Users switch or target agents with two in-chat commands:
 *
 *   /use agentName        — switch the session's active agent permanently
 *   @agentName message    — send one message to a specific agent without switching
 *   (plain text)          — goes to the current active agent
 *
 * Agent instances are per-session, not shared.  When a session first uses a named
 * agent, a fresh ChatAgent is created via ChatAgentFactory with its own isolated
 * 200-message memory window.  Two sessions using "research" have separate memories.
 *
 * Routing table (checked in order):
 *   1. Starts with "/use "  → switchAgent command
 *   2. Starts with "@"      → one-shot route to named agent
 *   3. Otherwise            → route to activeAgentName
 */
@Slf4j
public class AgentSession {

    @Getter private final String conversationId;
    @Getter private final Instant createdAt;
    @Getter private volatile Instant lastActiveAt;
    @Getter private volatile String activeAgentName;

    private final ChatAgentFactory agentFactory;
    private final AgentRegistry agentRegistry;

    /** Per-session agent instances: agentName → ChatAgent (lazy-created on first use). */
    private final ConcurrentHashMap<String, ChatAgent> agentInstances = new ConcurrentHashMap<>();

    public AgentSession(String conversationId,
                        ChatAgentFactory agentFactory,
                        AgentRegistry agentRegistry) {
        this.conversationId = conversationId;
        this.createdAt      = Instant.now();
        this.lastActiveAt   = Instant.now();
        this.agentFactory   = agentFactory;
        this.agentRegistry  = agentRegistry;
        this.activeAgentName = AgentRegistry.DEFAULT_AGENT;

        log.info("[Session] Created session='{}' active-agent='{}'",
                conversationId, activeAgentName);
    }

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Main entry point called by AgentDispatcher for every MESSAGE event.
     *
     * Routing rules (in priority order):
     *   /use agentName       → switch active agent, return confirmation
     *   /agents              → list all registered agents
     *   @agentName message   → one-shot route to named agent
     *   (anything else)      → route to activeAgentName
     */
    public String chat(String userMessage) {
        lastActiveAt = Instant.now();
        String msg = userMessage.trim();

        if (msg.startsWith("/use ")) {
            return handleUseCommand(msg.substring(5).trim());
        }
        if ("/agents".equalsIgnoreCase(msg)) {
            return handleListCommand();
        }
        if (msg.startsWith("@")) {
            int space = msg.indexOf(' ');
            if (space > 1) {
                String targetAgent = msg.substring(1, space).trim();
                String rest = msg.substring(space + 1).trim();
                return routeTo(targetAgent, rest);
            }
        }
        return routeTo(activeAgentName, msg);
    }

    // ------------------------------------------------------------------ //
    //  Command handlers
    // ------------------------------------------------------------------ //

    private String handleUseCommand(String agentName) {
        if (!agentRegistry.exists(agentName)) {
            return "❌ Agent **" + agentName + "** not found.\n" + buildAgentList();
        }
        activeAgentName = agentName;
        log.info("[Session] '{}' switched active agent to '{}'", conversationId, agentName);
        return "✅ Switched to agent **" + agentName + "**. "
                + "Your next messages will go to this agent.";
    }

    private String handleListCommand() {
        return "**Registered agents** (active: **" + activeAgentName + "**):\n"
                + buildAgentList()
                + "\n\nUse `/use agentName` to switch, or `@agentName message` to address one directly.";
    }

    // ------------------------------------------------------------------ //
    //  Routing
    // ------------------------------------------------------------------ //

    private String routeTo(String agentName, String message) {
        Optional<AgentDefinition> def = agentRegistry.get(agentName);
        if (def.isEmpty()) {
            return "❌ Agent **" + agentName + "** is not registered.\n" + buildAgentList();
        }

        // Lazy-create per-session instance for this agent.
        // Key = "conversationId:agentName" so persistence files stay scoped.
        ChatAgent agent = agentInstances.computeIfAbsent(agentName, name -> {
            String instanceId = conversationId + ":" + name;
            log.info("[Session] Creating instance of agent='{}' for session='{}'",
                    name, conversationId);
            return agentFactory.createChatAgent(instanceId, def.get().getDescription());
        });

        try {
            log.debug("[Session] Routing to agent='{}' in session='{}'", agentName, conversationId);
            return agent.chat(message);
        } catch (Exception e) {
            log.error("[Session] Agent='{}' call failed in session='{}'",
                    agentName, conversationId, e);
            return "Sorry, agent **" + agentName + "** encountered an error. Please try again.";
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private String buildAgentList() {
        return agentRegistry.all().stream()
                .map(d -> "• **" + d.getName() + "** — " + d.getDescription())
                .collect(Collectors.joining("\n"));
    }
}
