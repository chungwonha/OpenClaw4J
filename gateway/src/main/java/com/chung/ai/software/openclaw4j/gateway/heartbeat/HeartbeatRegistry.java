package com.chung.ai.software.openclaw4j.gateway.heartbeat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of heartbeat definitions.
 * Thread-safe — backed by ConcurrentHashMap.
 */
@Component
@Slf4j
public class HeartbeatRegistry {

    private final ConcurrentHashMap<String, HeartbeatDefinition> definitions = new ConcurrentHashMap<>();

    public HeartbeatDefinition register(HeartbeatDefinition definition) {
        definitions.put(definition.getId(), definition);
        log.info("[HeartbeatRegistry] Registered id='{}' name='{}' agent='{}' filter='{}'",
                definition.getId(), definition.getName(),
                definition.getAgentName(), definition.getSessionFilter());
        return definition;
    }

    public boolean unregister(String id) {
        boolean removed = definitions.remove(id) != null;
        if (removed) log.info("[HeartbeatRegistry] Unregistered id='{}'", id);
        return removed;
    }

    public Optional<HeartbeatDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public boolean exists(String id) {
        return definitions.containsKey(id);
    }

    public Collection<HeartbeatDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public int count() {
        return definitions.size();
    }
}
