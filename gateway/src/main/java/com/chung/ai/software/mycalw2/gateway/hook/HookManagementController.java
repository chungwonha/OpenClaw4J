package com.chung.ai.software.mycalw2.gateway.hook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for inspecting and toggling registered hooks at runtime.
 *
 * All state changes take effect immediately — the {@link HookRegistry} and
 * {@link HookDefinition#isEnabled()} flag are both thread-safe.
 *
 * Endpoints:
 *   GET  /api/hooks              — list all registered hooks (no handler exposure)
 *   PUT  /api/hooks/{name}/enable  — enable a named hook
 *   PUT  /api/hooks/{name}/disable — disable a named hook
 */
@RestController
@RequestMapping("/api/hooks")
@Slf4j
@RequiredArgsConstructor
public class HookManagementController {

    private final HookRegistry hookRegistry;

    /**
     * Returns summary projections for all registered hooks, sorted by name.
     */
    @GetMapping
    public List<HookDefinition.Summary> listHooks() {
        return hookRegistry.all();
    }

    /**
     * Enables the named hook.
     *
     * @param name hook name
     * @return 200 OK with confirmation message, or 404 if not found
     */
    @PutMapping("/{name}/enable")
    public ResponseEntity<String> enableHook(@PathVariable String name) {
        log.info("[HookManagementController] Request to enable hook='{}'", name);
        boolean found = hookRegistry.enable(name);
        if (!found) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("Hook '" + name + "' enabled.");
    }

    /**
     * Disables the named hook.
     *
     * @param name hook name
     * @return 200 OK with confirmation message, or 404 if not found
     */
    @PutMapping("/{name}/disable")
    public ResponseEntity<String> disableHook(@PathVariable String name) {
        log.info("[HookManagementController] Request to disable hook='{}'", name);
        boolean found = hookRegistry.disable(name);
        if (!found) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("Hook '" + name + "' disabled.");
    }
}
