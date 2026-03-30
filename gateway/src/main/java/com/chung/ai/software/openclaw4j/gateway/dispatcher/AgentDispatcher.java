package com.chung.ai.software.openclaw4j.gateway.dispatcher;

import com.chung.ai.software.openclaw4j.gateway.agent.AgentMessageContext;
import com.chung.ai.software.openclaw4j.gateway.cron.CronContext;
import com.chung.ai.software.openclaw4j.gateway.cron.CronDefinition;
import com.chung.ai.software.openclaw4j.gateway.cron.CronOutputService;
import com.chung.ai.software.openclaw4j.gateway.eventqueue.EventQueue;
import com.chung.ai.software.openclaw4j.gateway.eventqueue.GatewayEvent;
import com.chung.ai.software.openclaw4j.gateway.heartbeat.HeartbeatDefinition;
import com.chung.ai.software.openclaw4j.gateway.heartbeat.HeartbeatOutputService;
import com.chung.ai.software.openclaw4j.gateway.heartbeat.HeartbeatRegistry;
import com.chung.ai.software.openclaw4j.gateway.hook.HookEventType;
import com.chung.ai.software.openclaw4j.gateway.hook.HookExecutor;
import com.chung.ai.software.openclaw4j.gateway.integration.teams.TeamsActivity;
import com.chung.ai.software.openclaw4j.gateway.integration.teams.TeamsReplyService;
import com.chung.ai.software.openclaw4j.gateway.session.AgentSession;
import com.chung.ai.software.openclaw4j.gateway.session.SessionRegistry;
import com.chung.ai.software.openclaw4j.gateway.webhook.WebhookContext;
import com.chung.ai.software.openclaw4j.gateway.webhook.WebhookDefinition;
import com.chung.ai.software.openclaw4j.gateway.webhook.WebhookOutputService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The central event router — the "brain" of the gateway.
 *
 * Every 100 ms it drains the {@link EventQueue} and dispatches each event
 * to the right handler on its own thread, so that a slow LLM call for
 * one session never blocks processing for another.
 *
 * Supported event types:
 *  - MESSAGE    → route to the AgentSession for the conversation, send reply via Teams
 *  - HEARTBEAT  → log active sessions (extend this to trigger proactive checks)
 *  - CRON       → route to all active sessions with the scheduled prompt
 *  - HOOK       → handle internal lifecycle events
 *  - WEBHOOK    → handle inbound events from external systems
 *
 * Note: explicit constructor (not @RequiredArgsConstructor) is required so that
 * @Qualifier("gatewayExecutor") is placed directly on the constructor parameter —
 * the only location Spring reads it for disambiguation.  Lombok's generated
 * constructor does not propagate field-level @Qualifier annotations.
 */
@Component
@Slf4j
public class AgentDispatcher {

    private final EventQueue eventQueue;
    private final SessionRegistry sessionRegistry;
    private final TeamsReplyService teamsReplyService;
    private final WebhookOutputService webhookOutputService;
    private final CronOutputService cronOutputService;
    private final HeartbeatRegistry heartbeatRegistry;
    private final HeartbeatOutputService heartbeatOutputService;
    private final Executor executor;
    private final HookExecutor hookExecutor;

    public AgentDispatcher(EventQueue eventQueue,
                           SessionRegistry sessionRegistry,
                           TeamsReplyService teamsReplyService,
                           WebhookOutputService webhookOutputService,
                           CronOutputService cronOutputService,
                           HeartbeatRegistry heartbeatRegistry,
                           HeartbeatOutputService heartbeatOutputService,
                           @Qualifier("gatewayExecutor") Executor executor,
                           HookExecutor hookExecutor) {
        this.eventQueue = eventQueue;
        this.sessionRegistry = sessionRegistry;
        this.teamsReplyService = teamsReplyService;
        this.webhookOutputService = webhookOutputService;
        this.cronOutputService = cronOutputService;
        this.heartbeatRegistry = heartbeatRegistry;
        this.heartbeatOutputService = heartbeatOutputService;
        this.executor = executor;
        this.hookExecutor = hookExecutor;
    }

    /**
     * Drain the queue every 100 ms and dispatch each event on its own virtual thread.
     * Using fixedDelay (not fixedRate) avoids pile-up if processing is slow.
     */
    @Scheduled(fixedDelay = 100)
    public void drainQueue() {
        List<GatewayEvent> events = eventQueue.drainAll();
        for (GatewayEvent event : events) {
            CompletableFuture.runAsync(() -> dispatch(event), executor);
        }
    }

    private void dispatch(GatewayEvent event) {
        log.debug("[Dispatcher] Processing {} event for session='{}'", event.getType(), event.getSessionId());
        try {
            switch (event.getType()) {
                case MESSAGE       -> handleMessage(event);
                case HEARTBEAT     -> handleHeartbeat(event);
                case CRON          -> handleCron(event);
                case HOOK          -> handleHook(event);
                case WEBHOOK       -> handleWebhook(event);
                case AGENT_MESSAGE -> handleAgentMessage(event);
                default            -> log.warn("[Dispatcher] Unknown event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("[Dispatcher] Error processing {} event for session='{}'",
                    event.getType(), event.getSessionId(), e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Handlers
    // ------------------------------------------------------------------ //

    private void handleMessage(GatewayEvent event) {
        TeamsActivity activity = (TeamsActivity) event.getMetadata();
        AgentSession session = sessionRegistry.getOrCreate(event.getSessionId());

        String reply = session.chat(event.getPayload());
        teamsReplyService.sendReply(activity, reply);
    }

    private void handleHeartbeat(GatewayEvent event) {
        int count = sessionRegistry.count();
        log.info("[Dispatcher] Heartbeat — {} active session(s)", count);

        for (HeartbeatDefinition definition : heartbeatRegistry.all()) {
            if (!definition.isEnabled()) continue;

            Collection<AgentSession> targetSessions;
            if ("all".equals(definition.getSessionFilter())) {
                targetSessions = sessionRegistry.all();
            } else {
                AgentSession specific = sessionRegistry.get(definition.getSessionFilter());
                targetSessions = specific != null ? List.of(specific) : List.of();
            }

            for (AgentSession session : targetSessions) {
                final String sessionId = session.getConversationId();
                CompletableFuture.runAsync(() -> {
                    try {
                        String prompt = definition.getPromptTemplate()
                                .replace("{{sessionId}}", sessionId)
                                .replace("{{timestamp}}", event.getTimestamp().toString());
                        String result = session.chatWithAgent(definition.getAgentName(), prompt);
                        heartbeatOutputService.send(definition, sessionId, result);
                    } catch (Exception e) {
                        log.error("[Dispatcher] Heartbeat='{}' failed for session='{}'",
                                definition.getId(), sessionId, e);
                    }
                }, executor);
            }
        }
    }

    private void handleCron(GatewayEvent event) {
        CronContext context = (CronContext) event.getMetadata();
        CronDefinition definition = context.getDefinition();

        String prompt = definition.getPromptTemplate()
                .replace("{{cronName}}", definition.getName())
                .replace("{{timestamp}}", event.getTimestamp().toString());

        log.info("[Dispatcher] Cron='{}' routing to agent='{}' in session='{}'",
                definition.getId(), definition.getAgentName(), event.getSessionId());

        AgentSession session = sessionRegistry.getOrCreate(event.getSessionId());
        String result = session.chatWithAgent(definition.getAgentName(), prompt);
        cronOutputService.send(definition, result);
    }

    private void handleHook(GatewayEvent event) {
        String rawType = event.getPayload();
        try {
            HookEventType hookEventType = HookEventType.valueOf(rawType.toUpperCase());
            hookExecutor.fire(hookEventType, event.getSessionId(), event.getPayload());
        } catch (IllegalArgumentException e) {
            log.warn("[Dispatcher] Unknown hook event type in payload: {}", rawType);
        }
    }

    private void handleWebhook(GatewayEvent event) {
        WebhookContext context = (WebhookContext) event.getMetadata();
        WebhookDefinition definition = context.getDefinition();

        String prompt = definition.getPromptTemplate()
                .replace("{{payload}}",     context.getRawPayload())
                .replace("{{webhookName}}", definition.getName())
                .replace("{{timestamp}}",   event.getTimestamp().toString());

        log.info("[Dispatcher] Webhook='{}' routing to agent='{}' in session='{}'",
                definition.getId(), definition.getAgentName(), event.getSessionId());

        AgentSession session = sessionRegistry.getOrCreate(event.getSessionId());
        String result = session.chatWithAgent(definition.getAgentName(), prompt);
        webhookOutputService.send(definition, result);
    }

    private void handleAgentMessage(GatewayEvent event) {
        AgentMessageContext ctx = (AgentMessageContext) event.getMetadata();
        AgentSession session = sessionRegistry.getOrCreate(event.getSessionId());
        String result = session.chatWithAgent(ctx.getTargetAgentName(), event.getPayload());

        // If there's a replySession, deliver the result there too
        if (ctx.getReplySessionId() != null && !ctx.getReplySessionId().equals(event.getSessionId())) {
            AgentSession replySession = sessionRegistry.get(ctx.getReplySessionId());
            if (replySession != null) {
                replySession.chatWithAgent(ctx.getFromAgentName(),
                        "Task delegation result from @" + ctx.getTargetAgentName() + ": " + result);
            }
        }
        log.info("[Dispatcher] AGENT_MESSAGE from='{}' to='{}' completed",
                ctx.getFromAgentName(), ctx.getTargetAgentName());
    }
}
