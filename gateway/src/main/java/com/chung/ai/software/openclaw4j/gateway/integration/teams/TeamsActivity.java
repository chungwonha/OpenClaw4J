package com.chung.ai.software.openclaw4j.gateway.integration.teams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents an incoming Bot Framework Activity v3 from Microsoft Teams.
 *
 * Teams sends a POST to /api/messages with this JSON body whenever the bot
 * receives a message, a user joins/leaves a conversation, etc.
 *
 * Only the fields we actually use are mapped; everything else is ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamsActivity {

    /** Activity type — "message", "conversationUpdate", "invoke", etc. */
    private String type;

    /** Unique ID of this activity, used as replyToId when responding. */
    private String id;

    /** ISO-8601 timestamp when the activity was generated. */
    private String timestamp;

    /**
     * The Bot Framework service endpoint to use for outbound replies.
     * This URL is specific to the channel / tenant and must be honoured
     * exactly as received — do not hard-code it.
     */
    private String serviceUrl;

    /** Always "msteams" for MS Teams activities. */
    private String channelId;

    /** The user who sent the message. */
    private ChannelAccount from;

    /** Identifies the conversation (DM or channel thread). */
    private Conversation conversation;

    /** The bot itself (recipient of the incoming activity). */
    private ChannelAccount recipient;

    /** Plain-text message content (null for non-message activities). */
    private String text;

    /** ID of the activity being replied to. */
    private String replyToId;

    // ------------------------------------------------------------------ //

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChannelAccount {
        private String id;
        private String name;
        @JsonProperty("aadObjectId")
        private String aadObjectId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Conversation {
        private String id;
        private String tenantId;
        @JsonProperty("isGroup")
        private boolean group;
        private String conversationType;
    }
}
