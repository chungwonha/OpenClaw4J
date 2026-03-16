package com.chung.ai.software.openclaw4j.gateway.adapter.input;

import com.chung.ai.software.openclaw4j.gateway.cron.CronDefinition;
import com.chung.ai.software.openclaw4j.gateway.cron.CronRegistry;
import com.chung.ai.software.openclaw4j.gateway.cron.CronScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.UUID;

/**
 * REST API for managing cron job definitions at runtime.
 *
 * Endpoints:
 *   GET    /api/cron-definitions          — list all registered cron jobs
 *   GET    /api/cron-definitions/{id}     — get a specific cron job
 *   POST   /api/cron-definitions          — register a new cron job
 *   DELETE /api/cron-definitions/{id}     — remove a cron job
 *   PUT    /api/cron-definitions/{id}/enable   — enable and schedule a cron job
 *   PUT    /api/cron-definitions/{id}/disable  — disable and cancel a cron job
 *
 * Creating a cron job:
 * {
 *   "name":           "daily-summary",              (required)
 *   "description":    "Sends a daily report",        (optional)
 *   "agentName":      "default",                     (optional, defaults to "default")
 *   "sessionId":      null,                          (optional, null = dedicated cron session)
 *   "cronExpression": "0 0 9 * * MON-FRI",           (required, Spring 6-field cron)
 *   "promptTemplate": "Generate a daily summary.\nTimestamp: {{timestamp}}", (optional)
 *   "outputTarget":   "LOG",                         (optional: NONE | LOG | REPLY_URL)
 *   "replyUrl":       null                           (required if outputTarget=REPLY_URL)
 * }
 *
 * Cron expression format (6 fields): second minute hour day-of-month month day-of-week
 *   "0 * * * * *"        — every minute
 *   "0 0 * * * *"        — every hour
 *   "0 0 9 * * MON-FRI"  — 9am on weekdays
 */
@RestController
@RequestMapping("/api/cron-definitions")
@Slf4j
@RequiredArgsConstructor
public class CronManagementController {

    private final CronRegistry cronRegistry;
    private final CronScheduler cronScheduler;

    @GetMapping
    public Collection<CronDefinition> listAll() {
        return cronRegistry.all();
    }

    @GetMapping("/{id}")
    public CronDefinition getById(@PathVariable String id) {
        return cronRegistry.get(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Cron job not found: " + id));
    }

    @PostMapping
    public ResponseEntity<CronDefinition> register(@RequestBody CronRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'name' is required");
        }
        if (request.cronExpression() == null || request.cronExpression().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'cronExpression' is required");
        }
        if (!CronExpression.isValidExpression(request.cronExpression())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid cron expression: '" + request.cronExpression() + "'. " +
                    "Use 6-field Spring format: second minute hour day-of-month month day-of-week");
        }

        CronDefinition.OutputTarget target =
                request.outputTarget() != null ? request.outputTarget() : CronDefinition.OutputTarget.LOG;

        if (target == CronDefinition.OutputTarget.REPLY_URL
                && (request.replyUrl() == null || request.replyUrl().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'replyUrl' is required when outputTarget is REPLY_URL");
        }

        boolean enabled = request.enabled() == null || request.enabled();

        CronDefinition.CronDefinitionBuilder builder = CronDefinition.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description() != null ? request.description() : "")
                .agentName(request.agentName() != null ? request.agentName() : "default")
                .sessionId(request.sessionId())
                .cronExpression(request.cronExpression())
                .outputTarget(target)
                .replyUrl(request.replyUrl())
                .enabled(enabled);

        if (request.promptTemplate() != null && !request.promptTemplate().isBlank()) {
            builder.promptTemplate(request.promptTemplate());
        }

        CronDefinition definition = builder.build();
        cronRegistry.register(definition);
        cronScheduler.schedule(definition);

        log.info("[CronManagement] Created cron job id='{}' name='{}' cron='{}'",
                definition.getId(), definition.getName(), definition.getCronExpression());
        return ResponseEntity.status(HttpStatus.CREATED).body(definition);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable String id) {
        if (!cronRegistry.exists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cron job not found: " + id);
        }
        cronScheduler.cancel(id);
        cronRegistry.unregister(id);
    }

    @PutMapping("/{id}/enable")
    public CronDefinition enable(@PathVariable String id) {
        CronDefinition definition = cronRegistry.get(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Cron job not found: " + id));
        definition.setEnabled(true);
        cronScheduler.schedule(definition);
        log.info("[CronManagement] Enabled cron job id='{}'", id);
        return definition;
    }

    @PutMapping("/{id}/disable")
    public CronDefinition disable(@PathVariable String id) {
        CronDefinition definition = cronRegistry.get(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Cron job not found: " + id));
        definition.setEnabled(false);
        cronScheduler.cancel(id);
        log.info("[CronManagement] Disabled cron job id='{}'", id);
        return definition;
    }

    record CronRequest(
            String name,
            String description,
            String agentName,
            String sessionId,
            String cronExpression,
            String promptTemplate,
            CronDefinition.OutputTarget outputTarget,
            String replyUrl,
            Boolean enabled
    ) {}
}
