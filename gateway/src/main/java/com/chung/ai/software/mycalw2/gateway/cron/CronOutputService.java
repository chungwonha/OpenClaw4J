package com.chung.ai.software.mycalw2.gateway.cron;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Handles delivery of an agent's response after a cron job is processed.
 *
 * Three output strategies:
 *  NONE      — result is discarded
 *  LOG       — result is written to the application log at INFO level
 *  REPLY_URL — result is HTTP POSTed as JSON to definition.replyUrl
 *
 * REPLY_URL POST body:
 * {
 *   "cronId":   "...",
 *   "cronName": "...",
 *   "result":   "... agent response ..."
 * }
 */
@Component
@Slf4j
public class CronOutputService {

    private final WebClient webClient = WebClient.builder().build();

    public void send(CronDefinition definition, String result) {
        switch (definition.getOutputTarget()) {
            case NONE ->
                log.debug("[CronOutput] Result discarded (NONE) for cron='{}'", definition.getId());
            case LOG ->
                log.info("[CronOutput] cron='{}' ({}): {}", definition.getId(), definition.getName(), result);
            case REPLY_URL ->
                postToReplyUrl(definition, result);
        }
    }

    private void postToReplyUrl(CronDefinition definition, String result) {
        if (definition.getReplyUrl() == null || definition.getReplyUrl().isBlank()) {
            log.warn("[CronOutput] outputTarget=REPLY_URL but replyUrl not set for cron='{}'",
                    definition.getId());
            log.info("[CronOutput] Falling back to LOG for cron='{}': {}", definition.getId(), result);
            return;
        }

        Map<String, String> body = Map.of(
                "cronId",   definition.getId(),
                "cronName", definition.getName(),
                "result",   result
        );

        webClient.post()
                .uri(definition.getReplyUrl())
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> log.info("[CronOutput] Posted result to '{}' for cron='{}'",
                                definition.getReplyUrl(), definition.getId()),
                        error    -> log.error("[CronOutput] Failed to post to '{}' for cron='{}'",
                                definition.getReplyUrl(), definition.getId(), error)
                );
    }
}
