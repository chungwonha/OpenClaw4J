package com.chung.ai.software.mycalw2.gateway.session;

import com.chung.ai.software.mycalw2.ChatAgent;
import com.chung.ai.software.mycalw2.ChatAgentFactory;
import com.chung.ai.software.mycalw2.gateway.agent.AgentDefinition;
import com.chung.ai.software.mycalw2.gateway.agent.AgentRegistry;
import com.chung.ai.software.mycalw2.gateway.hook.HookEventType;
import com.chung.ai.software.mycalw2.gateway.hook.HookExecutor;
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
 *   1. Equals "/new"       → reset session, fire COMMAND_NEW hook
 *   2. Starts with "/use " → switchAgent command
 *   3. Equals "/agents"    → list all registered agents
 *   4. Equals "/reset"     → reset current agent, fire COMMAND_RESET hook
 *   5. Equals "/stop"      → stop session, fire COMMAND_STOP hook
 *   6. Starts with "@"     → one-shot route to named agent
 *   7. Otherwise           → route to activeAgentName
 */
@Slf4j
public class AgentSession {

    @Getter private final String conversationId;
    @Getter private final Instant createdAt;
    @Getter private volatile Instant lastActiveAt;
    @Getter private volatile String activeAgentName;

    private final ChatAgentFactory agentFactory;
    private final AgentRegistry agentRegistry;
    private final HookExecutor hookExecutor;

    /** Per-session agent instances: agentName → ChatAgent (lazy-created on first use). */
    private final ConcurrentHashMap<String, ChatAgent> agentInstances = new ConcurrentHashMap<>();

    public AgentSession(String conversationId,
                        ChatAgentFactory agentFactory,
                        AgentRegistry agentRegistry,
                        HookExecutor hookExecutor) {
        this.conversationId  = conversationId;
        this.createdAt       = Instant.now();
        this.lastActiveAt    = Instant.now();
        this.agentFactory    = agentFactory;
        this.agentRegistry   = agentRegistry;
        this.hookExecutor    = hookExecutor;
        this.activeAgentName = AgentRegistry.DEFAULT_AGENT;

        log.info("[Session] Created session='{}' active-agent='{}'",
                conversationId, activeAgentName);

        hookExecutor.fire(HookEventType.SESSION_START, conversationId, "Session started");
    }

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Main entry point called by AgentDispatcher for every MESSAGE event.
     *
     * Routing rules (in priority order):
     *   /new                 → reset session (clear all agents), fire COMMAND_NEW hook
     *   /use agentName       → switch active agent, return confirmation
     *   /agents              → list all registered agents
     *   /reset               → reset current agent (clear its memory), fire COMMAND_RESET hook
     *   /stop                → stop session, fire COMMAND_STOP hook
     *   @agentName message   → one-shot route to named agent
     *   (anything else)      → route to activeAgentName
     */
    public String chat(String userMessage) {
        lastActiveAt = Instant.now();
        String msg = userMessage.trim();

        if ("/new".equalsIgnoreCase(msg)) {
            return handleNewCommand();
        }
        if (msg.startsWith("/use ")) {
            return handleUseCommand(msg.substring(5).trim());
        }
        if ("/agents".equalsIgnoreCase(msg)) {
            return handleListCommand();
        }
        if ("/reset".equalsIgnoreCase(msg)) {
            return handleResetCommand();
        }
        if ("/stop".equalsIgnoreCase(msg)) {
            return handleStopCommand();
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

    private String handleNewCommand() {
        hookExecutor.fire(HookEventType.COMMAND_NEW, conversationId, "");
        agentInstances.clear();
        activeAgentName = AgentRegistry.DEFAULT_AGENT;
        log.info("[Session] '{}' executed /new — all agent instances cleared", conversationId);
        return "✅ Session reset. Starting fresh.";
    }

    private String handleResetCommand() {
        hookExecutor.fire(HookEventType.COMMAND_RESET, conversationId, activeAgentName);
        agentInstances.remove(activeAgentName);
        log.info("[Session] '{}' executed /reset — agent='{}' instance removed", conversationId, activeAgentName);
        return "✅ Agent **" + activeAgentName + "** reset. Memory cleared for this agent.";
    }

    private String handleStopCommand() {
        hookExecutor.fire(HookEventType.COMMAND_STOP, conversationId, "");
        log.info("[Session] '{}' executed /stop", conversationId);
        return "✅ Session stopped. Type any message to start a new session.";
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
            hookExecutor.fire(HookEventType.AGENT_BOOTSTRAP, conversationId,
                    "Bootstrapping agent: " + name);
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
