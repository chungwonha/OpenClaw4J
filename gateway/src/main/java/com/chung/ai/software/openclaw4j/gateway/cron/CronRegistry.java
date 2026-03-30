package com.chung.ai.software.openclaw4j.gateway.cron;

import com.chung.ai.software.openclaw4j.gateway.persistence.RegistryPersistenceService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of cron job definitions.
 * Thread-safe — backed by ConcurrentHashMap.
 */
@Component
@Slf4j
public class CronRegistry {

    private static final String PERSISTENCE_TYPE = "crons";

    private final ConcurrentHashMap<String, CronDefinition> definitions = new ConcurrentHashMap<>();
    private final RegistryPersistenceService persistence;

    @Autowired
    @Lazy
    private CronScheduler cronScheduler;

    public CronRegistry(RegistryPersistenceService persistence) {
        this.persistence = persistence;
    }

    @PostConstruct
    public void loadPersistedDefinitions() {
        List<CronDefinition> saved = persistence.loadAll(PERSISTENCE_TYPE, CronDefinition.class);
        for (CronDefinition definition : saved) {
            definitions.put(definition.getId(), definition);
            cronScheduler.schedule(definition);
            log.info("[CronRegistry] Restored and scheduled cron='{}' name='{}'",
                    definition.getId(), definition.getName());
        }
        log.info("[CronRegistry] Loaded {} persisted cron job(s)", saved.size());
    }

    public CronDefinition register(CronDefinition definition) {
        definitions.put(definition.getId(), definition);
        log.info("[CronRegistry] Registered id='{}' name='{}' cron='{}' agent='{}'",
                definition.getId(), definition.getName(),
                definition.getCronExpression(), definition.getAgentName());
        persistence.save(PERSISTENCE_TYPE, definition.getId(), definition);
        return definition;
    }

    public boolean unregister(String id) {
        boolean removed = definitions.remove(id) != null;
        if (removed) {
            log.info("[CronRegistry] Unregistered id='{}'", id);
            persistence.delete(PERSISTENCE_TYPE, id);
        }
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
