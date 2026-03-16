package com.chung.ai.software.openclaw4j.gateway.adapter.input;

import com.chung.ai.software.openclaw4j.gateway.webhook.WebhookDefinition;
import com.chung.ai.software.openclaw4j.gateway.webhook.WebhookRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.UUID;

/**
 * REST API for managing webhook definitions at runtime.
 *
 * Endpoints:
 *   GET    /api/webhook-definitions          — list all registered webhooks
 *   GET    /api/webhook-definitions/{id}     — get a specific webhook
 *   POST   /api/webhook-definitions          — register a new webhook
 *   DELETE /api/webhook-definitions/{id}     — remove a webhook
 *
 * Creating a webhook:
 * {
 *   "name":           "ci-pipeline-alert",         (required)
 *   "description":    "GitHub Actions failure",     (optional)
 *   "agentName":      "default",                    (optional, defaults to "default")
 *   "sessionId":      null,                         (optional, null = dedicated webhook session)
 *   "promptTemplate": "CI build failed:\n{{payload}}", (required)
 *   "outputTarget":   "LOG",                        (optional: NONE | LOG | REPLY_URL)
 *   "replyUrl":       null                          (required if outputTarget=REPLY_URL)
 * }
 *
 * Prompt template placeholders:
 *   {{payload}}      — the raw request body from the triggering system
 *   {{webhookName}}  — this webhook's name field
 *   {{timestamp}}    — ISO-8601 timestamp when the event was received
 */
@RestController
@RequestMapping("/api/webhook-definitions")
@Slf4j
@RequiredArgsConstructor
public class WebhookManagementController {

    private final WebhookRegistry webhookRegistry;

    @GetMapping
    public Collection<WebhookDefinition> listAll() {
        return webhookRegistry.all();
    }

    @GetMapping("/{id}")
    public WebhookDefinition getById(@PathVariable String id) {
        return webhookRegistry.get(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Webhook not found: " + id));
    }

    @PostMapping
    public ResponseEntity<WebhookDefinition> register(@RequestBody WebhookRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'name' is required");
        }
        if (request.promptTemplate() == null || request.promptTemplate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'promptTemplate' is required");
        }
        WebhookDefinition.OutputTarget target =
                request.outputTarget() != null ? request.outputTarget() : WebhookDefinition.OutputTarget.LOG;

        if (target == WebhookDefinition.OutputTarget.REPLY_URL
                && (request.replyUrl() == null || request.replyUrl().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'replyUrl' is required when outputTarget is REPLY_URL");
        }

        WebhookDefinition definition = WebhookDefinition.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description() != null ? request.description() : "")
                .agentName(request.agentName() != null ? request.agentName() : "default")
                .sessionId(request.sessionId())
                .promptTemplate(request.promptTemplate())
                .outputTarget(target)
                .replyUrl(request.replyUrl())
                .build();

        webhookRegistry.register(definition);
        log.info("[WebhookManagement] Created webhook id='{}' name='{}'",
                definition.getId(), definition.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(definition);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable String id) {
        if (!webhookRegistry.unregister(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook not found: " + id);
        }
    }

    record WebhookRequest(
            String name,
            String description,
            String agentName,
            String sessionId,
            String promptTemplate,
            WebhookDefinition.OutputTarget outputTarget,
            String replyUrl
    ) {}
}
