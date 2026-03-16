package com.chung.ai.software.openclaw4j.gateway.cron;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of cron job definitions.
 * Thread-safe — backed by ConcurrentHashMap.
 */
@Component
@Slf4j
public class CronRegistry {

    private final ConcurrentHashMap<String, CronDefinition> definitions = new ConcurrentHashMap<>();

    public CronDefinition register(CronDefinition definition) {
        definitions.put(definition.getId(), definition);
        log.info("[CronRegistry] Registered id='{}' name='{}' cron='{}' agent='{}'",
                definition.getId(), definition.getName(),
                definition.getCronExpression(), definition.getAgentName());
        return definition;
    }

    public boolean unregister(String id) {
        boolean removed = definitions.remove(id) != null;
        if (removed) log.info("[CronRegistry] Unregistered id='{}'", id);
        return removed;
    }

    public Optional<CronDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public boolean exists(String id) {
        return definitions.containsKey(id);
    }

    public Collection<CronDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public int count() {
        return definitions.size();
    }
}
