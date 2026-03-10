package com.chung.ai.software.mycalw2.gateway.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central store for all registered {@link HookDefinition}s.
 *
 * Thread-safe by virtue of the underlying {@link ConcurrentHashMap}.
 * Enable/disable operations update the {@code volatile boolean enabled} flag
 * on the definition directly, so changes are immediately visible to executor
 * threads without additional synchronisation.
 */
@Component
@Slf4j
public class HookRegistry {

    private final ConcurrentHashMap<String, HookDefinition> hooks = new ConcurrentHashMap<>();

    /**
     * Registers a hook definition, replacing any existing entry with the same
     * name.  Logs at INFO so operators can confirm which hooks are active at
     * startup.
     *
     * @param definition the hook to register; must not be null
     */
    public void register(HookDefinition definition) {
        hooks.put(definition.getName(), definition);
        log.info("[HookRegistry] Registered hook='{}' event={} enabled={}",
                definition.getName(), definition.getEventType(), definition.isEnabled());
    }

    /**
     * Enables the named hook.
     *
     * @param name hook name
     * @return {@code true} if the hook was found and enabled; {@code false} if
     *         no hook with that name is registered
     */
    public boolean enable(String name) {
        HookDefinition def = hooks.get(name);
        if (def == null) {
            log.warn("[HookRegistry] enable() — hook='{}' not found", name);
            return false;
        }
        def.setEnabled(true);
        log.info("[HookRegistry] Enabled hook='{}'", name);
        return true;
    }

    /**
     * Disables the named hook.
     *
     * @param name hook name
     * @return {@code true} if the hook was found and disabled; {@code false} if
     *         no hook with that name is registered
     */
    public boolean disable(String name) {
        HookDefinition def = hooks.get(name);
        if (def == null) {
            log.warn("[HookRegistry] disable() — hook='{}' not found", name);
            return false;
        }
        def.setEnabled(false);
        log.info("[HookRegistry] Disabled hook='{}'", name);
        return true;
    }

    /**
     * Returns whether a hook with the given name is registered.
     *
     * @param name hook name
     * @return {@code true} if present
     */
    public boolean exists(String name) {
        return hooks.containsKey(name);
    }

    /**
     * Returns summary projections for all registered hooks, sorted by name.
     *
     * @return immutable sorted list of {@link HookDefinition.Summary}
     */
    public List<HookDefinition.Summary> all() {
        return hooks.values().stream()
                .sorted(Comparator.comparing(HookDefinition::getName))
                .map(HookDefinition::toSummary)
                .toList();
    }

    /**
     * Returns all enabled hook definitions registered for the given event type.
     * Used by {@link HookExecutor} to find the handlers to invoke.
     *
     * @param eventType the lifecycle event
     * @return list of enabled definitions; may be empty, never null
     */
    public List<HookDefinition> getEnabledHandlersFor(HookEventType eventType) {
        return hooks.values().stream()
                .filter(HookDefinition::isEnabled)
                .filter(d -> d.getEventType() == eventType)
                .toList();
    }
}
