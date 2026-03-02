package com.chung.ai.software.mycalw2.gateway.integration.teams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

/**
 * Obtains and caches the OAuth 2.0 bearer token required to call the
 * Bot Framework Service API (for sending replies back to Teams).
 *
 * Grant type: client_credentials
 * Endpoint:   https://login.microsoftonline.com/botframework.com/oauth2/v2.0/token
 */
@Service
@Slf4j
public class TeamsTokenService {

    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/botframework.com/oauth2/v2.0/token";
    private static final String SCOPE =
            "https://api.botframework.com/.default";

    @Value("${microsoft.app.id:}")
    private String appId;

    @Value("${microsoft.app.password:}")
    private String appPassword;

    private final WebClient webClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public TeamsTokenService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    /**
     * Returns a valid bearer token, refreshing it automatically when it is
     * within 60 seconds of expiry.  Returns {@code null} when credentials
     * are not configured (local dev / mock mode).
     */
    public synchronized String getToken() {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appPassword)) {
            log.warn("Microsoft App ID/Password not configured — skipping token fetch. " +
                     "Set MICROSOFT_APP_ID and MICROSOFT_APP_PASSWORD to enable real Teams replies.");
            return null;
        }

        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }

        log.debug("Fetching new Bot Framework OAuth token for appId={}", appId);
        try {
            TokenResponse response = webClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                            .with("client_id", appId)
                            .with("client_secret", appPassword)
                            .with("scope", SCOPE))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (response != null && StringUtils.hasText(response.getAccessToken())) {
                cachedToken = response.getAccessToken();
                tokenExpiry = Instant.now().plusSeconds(response.getExpiresIn());
                log.info("Bot Framework token obtained, expires in {}s", response.getExpiresIn());
            }
        } catch (Exception e) {
            log.error("Failed to obtain Bot Framework token", e);
        }

        return cachedToken;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("expires_in")
        private long expiresIn;
    }
}
