package com.chung.ai.software.mycalw2.gateway.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Handles delivery of an agent's response after a webhook event is processed.
 *
 * Three output strategies:
 *  NONE      — result is discarded (fire-and-forget automation)
 *  LOG       — result is written to the application log at INFO level
 *  REPLY_URL — result is HTTP POSTed as JSON to definition.replyUrl
 *
 * The REPLY_URL POST body:
 * {
 *   "webhookId":   "...",
 *   "webhookName": "...",
 *   "result":      "... agent response ..."
 * }
 */
@Component
@Slf4j
public class WebhookOutputService {

    private final WebClient webClient = WebClient.builder().build();

    public void send(WebhookDefinition definition, String result) {
        switch (definition.getOutputTarget()) {
            case NONE ->
                log.debug("[WebhookOutput] Result discarded (NONE) for webhook='{}'", definition.getId());
            case LOG ->
                log.info("[WebhookOutput] webhook='{}' ({}): {}", definition.getId(), definition.getName(), result);
            case REPLY_URL ->
                postToReplyUrl(definition, result);
        }
    }

    private void postToReplyUrl(WebhookDefinition definition, String result) {
        if (definition.getReplyUrl() == null || definition.getReplyUrl().isBlank()) {
            log.warn("[WebhookOutput] outputTarget=REPLY_URL but replyUrl not set for webhook='{}'",
                    definition.getId());
            log.info("[WebhookOutput] Falling back to LOG for webhook='{}': {}", definition.getId(), result);
            return;
        }

        Map<String, String> body = Map.of(
                "webhookId",   definition.getId(),
                "webhookName", definition.getName(),
                "result",      result
        );

        webClient.post()
                .uri(definition.getReplyUrl())
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> log.info("[WebhookOutput] Posted result to '{}' for webhook='{}'",
                                definition.getReplyUrl(), definition.getId()),
                        error   -> log.error("[WebhookOutput] Failed to post to '{}' for webhook='{}'",
                                definition.getReplyUrl(), definition.getId(), error)
                );
    }
}
