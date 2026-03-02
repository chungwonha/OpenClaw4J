package com.chung.ai.software.mycalw2.gateway.session;

import com.chung.ai.software.mycalw2.ChatAgent;
import com.chung.ai.software.mycalw2.ChatAgentFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * One session = one Teams conversation (DM or channel thread).
 *
 * Each session wraps a {@link ChatAgent} produced by {@link ChatAgentFactory}.
 * The ChatAgent comes with:
 *   - 200-message memory window — conversation context is preserved across turns
 *   - FileManagementTool  — read / write files on the host machine
 *   - HttpRequestTool     — make outbound HTTP GET / POST / PUT / DELETE calls
 *   - CommandLineTool     — run OS commands
 *   - MCP registry        — attach external tool servers at runtime
 *
 * Sessions are created lazily by {@link SessionRegistry} on the first message
 * for a given conversationId, mirroring OpenClaw's per-channel isolation.
 */
@Slf4j
public class AgentSession {

    @Getter
    private final String conversationId;

    @Getter
    private final Instant createdAt;

    @Getter
    private volatile Instant lastActiveAt;

    private final ChatAgent chatAgent;

    public AgentSession(String conversationId, ChatAgentFactory agentFactory) {
        this.conversationId = conversationId;
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();

        // Use conversationId as the agent name so persistence files (agents/<id>.md)
        // are scoped to the conversation.
        this.chatAgent = agentFactory.createChatAgent(
                conversationId,
                "Teams AI assistant — conversation " + conversationId
        );

        log.info("[Session] Created agent for conversation '{}' (file + HTTP + CLI tools active)",
                conversationId);
    }

    /**
     * Send a message to this session's agent and return its reply.
     * The underlying ChatAgent maintains its own memory, so context from
     * earlier messages in the same conversation is automatically preserved.
     */
    public String chat(String userMessage) {
        lastActiveAt = Instant.now();
        try {
            return chatAgent.chat(userMessage);
        } catch (Exception e) {
            log.error("[Session] Agent call failed for conversation '{}'", conversationId, e);
            return "Sorry, I encountered an error processing your request. Please try again.";
        }
    }
}
