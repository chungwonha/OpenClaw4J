package com.chung.ai.software.mycalw2.gateway.adapter.input;

import com.chung.ai.software.mycalw2.gateway.agent.AgentDefinition;
import com.chung.ai.software.mycalw2.gateway.agent.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * REST API for managing named agent definitions at runtime.
 *
 * Endpoints
 * ---------
 *   GET  /api/agents          — list all registered agents
 *   GET  /api/agents/{name}   — get a single agent by name
 *   POST /api/agents          — register a new agent (or overwrite existing)
 *   DELETE /api/agents/{name} — unregister an agent (cannot remove "default")
 *
 * Example — register a "research" agent:
 * <pre>
 *   POST /api/agents
 *   {
 *     "name": "research",
 *     "description": "Specialist in deep research and summarization. Use web search and cite sources."
 *   }
 * </pre>
 *
 * After registration, any Teams session can reach it with:
 *   /use research     — switch session's active agent
 *   @research <msg>  — one-shot message to that agent
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentManagementController {

    private final AgentRegistry agentRegistry;

    // ------------------------------------------------------------------ //
    //  GET /api/agents  — list all
    // ------------------------------------------------------------------ //

    @GetMapping
    public ResponseEntity<Collection<AgentDefinition>> listAgents() {
        return ResponseEntity.ok(agentRegistry.all());
    }

    // ------------------------------------------------------------------ //
    //  GET /api/agents/{name}  — single agent
    // ------------------------------------------------------------------ //

    @GetMapping("/{name}")
    public ResponseEntity<?> getAgent(@PathVariable String name) {
        return agentRegistry.get(name)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Agent '" + name + "' not found")));
    }

    // ------------------------------------------------------------------ //
    //  POST /api/agents  — register (create or overwrite)
    // ------------------------------------------------------------------ //

    /**
     * Request body: {@code { "name": "...", "description": "..." }}
     * <p>
     * Overwrites an existing definition by the same name (including its description).
     * Sessions that already have a running instance of that agent keep their instance
     * until their session is evicted; new sessions will pick up the updated description.
     */
    @PostMapping
    public ResponseEntity<?> registerAgent(@RequestBody AgentRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Field 'name' is required and must not be blank"));
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Field 'description' is required and must not be blank"));
        }

        boolean isNew = !agentRegistry.exists(request.getName());
        AgentDefinition saved = agentRegistry.register(
                AgentDefinition.builder()
                        .name(request.getName().trim())
                        .description(request.getDescription().trim())
                        .build());

        log.info("[AgentMgmt] Agent '{}' {} via REST", saved.getName(), isNew ? "registered" : "updated");
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(saved);
    }

    // ------------------------------------------------------------------ //
    //  DELETE /api/agents/{name}  — unregister
    // ------------------------------------------------------------------ //

    @DeleteMapping("/{name}")
    public ResponseEntity<?> unregisterAgent(@PathVariable String name) {
        if (AgentRegistry.DEFAULT_AGENT.equals(name)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot unregister the default agent"));
        }
        boolean removed = agentRegistry.unregister(name);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Agent '" + name + "' not found"));
        }
        log.info("[AgentMgmt] Agent '{}' unregistered via REST", name);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ //
    //  Inner DTO
    // ------------------------------------------------------------------ //

    /** Request body for POST /api/agents. */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AgentRequest {
        private String name;
        private String description;
    }
}
