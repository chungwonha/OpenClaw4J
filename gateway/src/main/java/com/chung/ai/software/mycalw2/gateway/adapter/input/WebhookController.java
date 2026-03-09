package com.chung.ai.software.mycalw2.gateway.adapter.input;

import com.chung.ai.software.mycalw2.gateway.eventqueue.EventQueue;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEvent;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEventType;
import com.chung.ai.software.mycalw2.gateway.webhook.WebhookContext;
import com.chung.ai.software.mycalw2.gateway.webhook.WebhookDefinition;
import com.chung.ai.software.mycalw2.gateway.webhook.WebhookRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Webhook input adapter — the HTTP entry point for external systems.
 *
 * External systems (CI pipelines, monitoring tools, Slack, etc.) POST their
 * event payloads to:
 *
 *   POST /api/webhooks/{webhookId}
 *
 * The controller:
 *  1. Looks up the WebhookDefinition by ID.
 *  2. Wraps the raw payload and definition in a GatewayEvent (type=WEBHOOK).
 *  3. Enqueues the event for async processing by AgentDispatcher.
 *  4. Returns 202 Accepted immediately — AI processing happens asynchronously.
 *
 * The agent's response is delivered via the output strategy configured on the
 * WebhookDefinition (LOG, REPLY_URL, or NONE).
 *
 * Payload format: any content-type is accepted as a raw string body.
 * JSON payloads are received as their string representation and passed
 * verbatim into the agent prompt template.
 */
@RestController
@RequestMapping("/api/webhooks")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final EventQueue eventQueue;
    private final WebhookRegistry webhookRegistry;

    @PostMapping(
            value = "/{webhookId}",
            consumes = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.TEXT_PLAIN_VALUE,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    MediaType.ALL_VALUE
            }
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void trigger(
            @PathVariable String webhookId,
            @RequestBody(required = false) String payload) {

        WebhookDefinition definition = webhookRegistry.get(webhookId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Webhook not found: " + webhookId));

        String rawPayload = payload != null ? payload.trim() : "";

        log.info("[Webhook] Trigger received: id='{}' name='{}' payload-length={}",
                webhookId, definition.getName(), rawPayload.length());

        // Dedicated session per webhook unless the definition pins to a specific sessionId
        String sessionId = definition.getSessionId() != null
                ? definition.getSessionId()
                : "webhook:" + webhookId;

        GatewayEvent event = GatewayEvent.builder()
                .type(GatewayEventType.WEBHOOK)
                .sessionId(sessionId)
                .payload(rawPayload)
                .metadata(WebhookContext.builder()
                        .definition(definition)
                        .rawPayload(rawPayload)
                        .build())
                .timestamp(Instant.now())
                .build();

        eventQueue.enqueue(event);
        log.debug("[Webhook] Enqueued WEBHOOK event for session='{}'", sessionId);
    }
}
