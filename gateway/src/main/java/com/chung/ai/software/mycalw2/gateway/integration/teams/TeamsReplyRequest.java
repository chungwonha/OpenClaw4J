package com.chung.ai.software.mycalw2.gateway.integration.teams;

import lombok.Builder;
import lombok.Data;

/**
 * Outbound Bot Framework Activity v3 — the JSON body we POST back to
 * Teams to deliver the agent's reply.
 *
 * POST {serviceUrl}/v3/conversations/{conversationId}/activities/{activityId}
 */
@Data
@Builder
public class TeamsReplyRequest {

    @Builder.Default
    private String type = "message";

    /** The bot (sender of the reply). */
    private TeamsActivity.ChannelAccount from;

    /** The conversation this reply belongs to. */
    private TeamsActivity.Conversation conversation;

    /** The original user (receiver of the reply). */
    private TeamsActivity.ChannelAccount recipient;

    /** The agent's reply text. */
    private String text;

    @Builder.Default
    private String textFormat = "plain";

    /** ID of the incoming activity we are replying to. */
    private String replyToId;
}
