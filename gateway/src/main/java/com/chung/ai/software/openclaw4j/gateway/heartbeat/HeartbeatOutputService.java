package com.chung.ai.software.openclaw4j.gateway.heartbeat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Handles delivery of an agent's response after a heartbeat task is processed.
 *
 * Three output strategies:
 *  NONE      — result is discarded
 *  LOG       — result is written to the application log at INFO level
 *  REPLY_URL — result is HTTP POSTed as JSON to definition.replyUrl
 *
 * REPLY_URL POST body:
 * {
 *   "heartbeatId":   "...",
 *   "heartbeatName": "...",
 *   "sessionId":     "...",
 *   "result":        "... agent response ..."
 * }
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HeartbeatOutputService {

    private final WebClient.Builder webClientBuilder;

    public void send(HeartbeatDefinition definition, String sessionId, String result) {
        switch (definition.getOutputTarget()) {
            case NONE ->
                log.debug("[HeartbeatOutput] Result discarded (NONE) for heartbeat='{}'", definition.getId());
            case LOG ->
                log.info("[HeartbeatOutput] heartbeat='{}' ({}) session='{}': {}",
                        definition.getId(), definition.getName(), sessionId, result);
            case REPLY_URL ->
                postToReplyUrl(definition, sessionId, result);
        }
    }

    private void postToReplyUrl(HeartbeatDefinition definition, String sessionId, String result) {
        if (definition.getReplyUrl() == null || definition.getReplyUrl().isBlank()) {
            log.warn("[HeartbeatOutput] outputTarget=REPLY_URL but replyUrl not set for heartbeat='{}'",
                    definition.getId());
            log.info("[HeartbeatOutput] Falling back to LOG for heartbeat='{}': {}",
                    definition.getId(), result);
            return;
        }

        Map<String, String> body = Map.of(
                "heartbeatId",   definition.getId(),
                "heartbeatName", definition.getName(),
                "sessionId",     sessionId,
                "result",        result
        );

        webClientBuilder.build()
                .post()
                .uri(definition.getReplyUrl())
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> log.info("[HeartbeatOutput] Posted result to '{}' for heartbeat='{}'",
                                definition.getReplyUrl(), definition.getId()),
                        error    -> log.error("[HeartbeatOutput] Failed to post to '{}' for heartbeat='{}'",
                                definition.getReplyUrl(), definition.getId(), error)
                );
    }
}
