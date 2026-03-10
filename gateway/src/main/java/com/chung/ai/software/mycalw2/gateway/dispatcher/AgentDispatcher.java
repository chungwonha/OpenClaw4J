package com.chung.ai.software.mycalw2.gateway.dispatcher;

import com.chung.ai.software.mycalw2.gateway.eventqueue.EventQueue;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEvent;
import com.chung.ai.software.mycalw2.gateway.hook.HookEventType;
import com.chung.ai.software.mycalw2.gateway.hook.HookExecutor;
import com.chung.ai.software.mycalw2.gateway.integration.teams.TeamsActivity;
import com.chung.ai.software.mycalw2.gateway.integration.teams.TeamsReplyService;
import com.chung.ai.software.mycalw2.gateway.session.AgentSession;
import com.chung.ai.software.mycalw2.gateway.session.SessionRegistry;
import com.chung.ai.software.mycalw2.gateway.webhook.WebhookContext;
import com.chung.ai.software.mycalw2.gateway.webhook.WebhookDefinition;
import com.chung.ai.software.mycalw2.gateway.webhook.WebhookOutputService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private final Executor executor;
    private final HookExecutor hookExecutor;

    public AgentDispatcher(EventQueue eventQueue,
                           SessionRegistry sessionRegistry,
                           TeamsReplyService teamsReplyService,
                           WebhookOutputService webhookOutputService,
                           @Qualifier("gatewayExecutor") Executor executor,
                           HookExecutor hookExecutor) {
        this.eventQueue = eventQueue;
        this.sessionRegistry = sessionRegistry;
        this.teamsReplyService = teamsReplyService;
        this.webhookOutputService = webhookOutputService;
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
                case MESSAGE    -> handleMessage(event);
                case HEARTBEAT  -> handleHeartbeat(event);
                case CRON       -> handleCron(event);
                case HOOK       -> handleHook(event);
                case WEBHOOK    -> handleWebhook(event);
                default         -> log.warn("[Dispatcher] Unknown event type: {}", event.getType());
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
        // TODO: iterate sessions and run proactive prompts (e.g. "Any urgent emails?")
        // For now we only log, consistent with an MVP that proves the event loop works.
    }

    private void handleCron(GatewayEvent event) {
        log.info("[Dispatcher] Cron event fired for session='{}': {}", event.getSessionId(), event.getPayload());
        // TODO: route the cron prompt to the specific session and send the result back
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
}
