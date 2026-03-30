package com.chung.ai.software.openclaw4j.gateway.adapter.input;

import com.chung.ai.software.openclaw4j.gateway.heartbeat.HeartbeatDefinition;
import com.chung.ai.software.openclaw4j.gateway.heartbeat.HeartbeatRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * REST API for managing heartbeat definitions at runtime.
 *
 * Endpoints:
 *   GET    /api/heartbeat-definitions          — list all registered heartbeat definitions
 *   GET    /api/heartbeat-definitions/{id}     — get a specific heartbeat definition
 *   POST   /api/heartbeat-definitions          — register a new heartbeat definition
 *   DELETE /api/heartbeat-definitions/{id}     — remove a heartbeat definition
 *   PUT    /api/heartbeat-definitions/{id}/enable   — enable a heartbeat definition
 *   PUT    /api/heartbeat-definitions/{id}/disable  — disable a heartbeat definition
 */
@RestController
@RequestMapping("/api/heartbeat-definitions")
@Slf4j
@RequiredArgsConstructor
public class HeartbeatManagementController {

    private final HeartbeatRegistry heartbeatRegistry;

    @GetMapping
    public Collection<HeartbeatDefinition> listAll() {
        return heartbeatRegistry.all();
    }

    @GetMapping("/{id}")
    public HeartbeatDefinition getById(@PathVariable String id) {
        return heartbeatRegistry.get(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Heartbeat definition not found: " + id));
    }

    @PostMapping
    public ResponseEntity<HeartbeatDefinition> register(@RequestBody HeartbeatRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'name' is required");
        }

        HeartbeatDefinition.OutputTarget target =
                request.outputTarget() != null ? request.outputTarget() : HeartbeatDefinition.OutputTarget.LOG;

        if (target == HeartbeatDefinition.OutputTarget.REPLY_URL
                && (request.replyUrl() == null || request.replyUrl().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'replyUrl' is required when outputTarget is REPLY_URL");
        }

        boolean enabled = request.enabled() == null || request.enabled();

        HeartbeatDefinition.HeartbeatDefinitionBuilder builder = HeartbeatDefinition.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description() != null ? request.description() : "")
                .agentName(request.agentName() != null ? request.agentName() : "default")
                .sessionFilter(request.sessionFilter() != null ? request.sessionFilter() : "all")
                .outputTarget(target)
                .replyUrl(request.replyUrl())
                .enabled(enabled)
                .registeredAt(Instant.now());

        if (request.promptTemplate() != null && !request.promptTemplate().isBlank()) {
            builder.promptTemplate(request.promptTemplate());
        }

        HeartbeatDefinition definition = builder.build();
        heartbeatRegistry.register(definition);

        log.info("[HeartbeatManagement] Created heartbeat definition id='{}' name='{}'",
                definition.getId(), definition.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(definition);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable String id) {
        if (!heartbeatRegistry.exists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Heartbeat definition not found: " + id);
        }
        heartbeatRegistry.unregister(id);
    }

    @PutMapping("/{id}/enable")
    public HeartbeatDefinition enable(@PathVariable String id) {
        HeartbeatDefinition definition = heartbeatRegistry.get(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Heartbeat definition not found: " + id));
        definition.setEnabled(true);
        log.info("[HeartbeatManagement] Enabled heartbeat definition id='{}'", id);
        return definition;
    }

    @PutMapping("/{id}/disable")
    public HeartbeatDefinition disable(@PathVariable String id) {
        HeartbeatDefinition definition = heartbeatRegistry.get(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Heartbeat definition not found: " + id));
        definition.setEnabled(false);
        log.info("[HeartbeatManagement] Disabled heartbeat definition id='{}'", id);
        return definition;
    }

    record HeartbeatRequest(
            String name,
            String description,
            String agentName,
            String sessionFilter,
            String promptTemplate,
            HeartbeatDefinition.OutputTarget outputTarget,
            String replyUrl,
            Boolean enabled
    ) {}
}
