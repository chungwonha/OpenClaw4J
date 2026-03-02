package com.chung.ai.software.mycalw2.gateway.adapter.input;

import com.chung.ai.software.mycalw2.gateway.eventqueue.EventQueue;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEvent;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEventType;
import com.chung.ai.software.mycalw2.gateway.integration.teams.TeamsActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * MS Teams input adapter.
 *
 * The Azure Bot Framework sends all Teams activity events (messages,
 * conversation-update events, etc.) as HTTP POST requests to this endpoint.
 *
 * Responsibilities:
 *  1. Accept the incoming {@link TeamsActivity} from Bot Framework.
 *  2. Normalise it into a {@link GatewayEvent} and push it onto the queue.
 *  3. Return 202 Accepted immediately — the actual AI response is sent
 *     asynchronously by the {@link com.chung.ai.software.mycalw2.gateway.dispatcher.AgentDispatcher}
 *     via the Bot Framework reply API.
 *
 * NOTE: For production you must validate the JWT bearer token in the
 * Authorization header against Bot Framework's OpenID metadata.
 * Set {@code gateway.teams.verify-token=true} and wire up a proper
 * JwtVerificationFilter when you move beyond local dev.
 */
@RestController
@RequestMapping("/api/messages")
@Slf4j
@RequiredArgsConstructor
public class TeamsController {

    private final EventQueue eventQueue;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void handleActivity(@RequestBody TeamsActivity activity) {
        log.info("[Teams] Received activity: type='{}', channelId='{}', conversationId='{}'",
                activity.getType(),
                activity.getChannelId(),
                activity.getConversation() != null ? activity.getConversation().getId() : "null");

        switch (activity.getType() != null ? activity.getType() : "") {
            case "message" -> handleMessage(activity);
            case "conversationUpdate" -> log.info("[Teams] ConversationUpdate — no action needed for MVP");
            default -> log.debug("[Teams] Ignoring activity type='{}'", activity.getType());
        }
    }

    private void handleMessage(TeamsActivity activity) {
        if (!StringUtils.hasText(activity.getText())) {
            log.debug("[Teams] Skipping message with empty text");
            return;
        }

        String conversationId = activity.getConversation().getId();

        GatewayEvent event = GatewayEvent.builder()
                .type(GatewayEventType.MESSAGE)
                .sessionId(conversationId)
                .payload(activity.getText().trim())
                .metadata(activity)
                .timestamp(Instant.now())
                .build();

        eventQueue.enqueue(event);
        log.debug("[Teams] Enqueued MESSAGE event for session='{}'", conversationId);
    }
}
