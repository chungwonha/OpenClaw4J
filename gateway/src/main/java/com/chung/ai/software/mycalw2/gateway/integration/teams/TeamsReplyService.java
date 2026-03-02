package com.chung.ai.software.mycalw2.gateway.integration.teams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Sends a reply back to the Bot Framework Service API.
 *
 * URL pattern:
 *   POST {serviceUrl}/v3/conversations/{conversationId}/activities/{activityId}
 *   Authorization: Bearer {token}   ← omitted in emulator / dev mode
 *
 * Two operating modes:
 *
 *  1. Emulator / dev mode (no MICROSOFT_APP_ID/PASSWORD configured):
 *     The Bot Framework Emulator acts as its own service endpoint and sets
 *     serviceUrl to something like http://localhost:50328.  It does NOT
 *     require a Bearer token.  We POST to it unauthenticated so replies
 *     show up in the emulator UI rather than just in the Spring Boot log.
 *
 *  2. Production (credentials configured):
 *     Fetch an OAuth token from Azure AD and include it as Bearer auth.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamsReplyService {

    private final WebClient.Builder webClientBuilder;
    private final TeamsTokenService tokenService;

    public void sendReply(TeamsActivity incomingActivity, String replyText) {
        String conversationId = incomingActivity.getConversation().getId();
        String activityId = incomingActivity.getId();
        String serviceUrl = incomingActivity.getServiceUrl();

        log.info("[Teams] Replying to conversation='{}', activityId='{}'", conversationId, activityId);
        log.debug("[Teams] Reply text: {}", replyText);

        TeamsActivity.ChannelAccount botAccount = new TeamsActivity.ChannelAccount();
        botAccount.setId(incomingActivity.getRecipient().getId());
        botAccount.setName(incomingActivity.getRecipient().getName());

        TeamsReplyRequest reply = TeamsReplyRequest.builder()
                .from(botAccount)
                .conversation(incomingActivity.getConversation())
                .recipient(incomingActivity.getFrom())
                .text(replyText)
                .replyToId(activityId)
                .build();

        String url = normaliseServiceUrl(serviceUrl)
                + "/v3/conversations/" + conversationId
                + "/activities/" + activityId;

        String token = tokenService.getToken();
        boolean hasToken = StringUtils.hasText(token);

        if (!hasToken) {
            log.info("[Teams] No credentials configured — sending unauthenticated reply (emulator mode)");
        }

        WebClient.RequestBodySpec request = webClientBuilder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);

        if (hasToken) {
            request = request.header("Authorization", "Bearer " + token);
        }

        request.bodyValue(reply)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                        resp -> log.info("[Teams] Reply delivered: {}", resp),
                        err  -> log.error("[Teams] Failed to deliver reply to '{}': {}", url, err.getMessage())
                );
    }

    private String normaliseServiceUrl(String serviceUrl) {
        // Strip trailing slash if present
        return serviceUrl != null && serviceUrl.endsWith("/")
                ? serviceUrl.substring(0, serviceUrl.length() - 1)
                : serviceUrl;
    }
}
