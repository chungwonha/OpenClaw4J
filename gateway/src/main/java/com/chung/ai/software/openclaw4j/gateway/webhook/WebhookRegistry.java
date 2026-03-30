package com.chung.ai.software.openclaw4j.gateway.webhook;

import com.chung.ai.software.openclaw4j.gateway.persistence.RegistryPersistenceService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of named webhook definitions.
 *
 * Thread-safe — backed by ConcurrentHashMap.
 * Webhooks are registered at runtime via the management REST API
 * (WebhookManagementController) and persist in-memory for the lifetime
 * of the gateway process.
 */
@Component
@Slf4j
public class WebhookRegistry {

    private static final String PERSISTENCE_TYPE = "webhooks";

    private final ConcurrentHashMap<String, WebhookDefinition> definitions = new ConcurrentHashMap<>();
    private final RegistryPersistenceService persistence;

    public WebhookRegistry(RegistryPersistenceService persistence) {
        this.persistence = persistence;
    }

    @PostConstruct
    public void loadPersistedDefinitions() {
        List<WebhookDefinition> saved = persistence.loadAll(PERSISTENCE_TYPE, WebhookDefinition.class);
        for (WebhookDefinition definition : saved) {
            definitions.put(definition.getId(), definition);
            log.info("[WebhookRegistry] Restored webhook='{}' name='{}'",
                    definition.getId(), definition.getName());
        }
        log.info("[WebhookRegistry] Loaded {} persisted webhook(s)", saved.size());
    }

    public WebhookDefinition register(WebhookDefinition definition) {
        definitions.put(definition.getId(), definition);
        log.info("[WebhookRegistry] Registered id='{}' name='{}' agent='{}' output='{}'",
                definition.getId(), definition.getName(),
                definition.getAgentName(), definition.getOutputTarget());
        persistence.save(PERSISTENCE_TYPE, definition.getId(), definition);
        return definition;
    }

    public boolean unregister(String id) {
        boolean removed = definitions.remove(id) != null;
        if (removed) {
            log.info("[WebhookRegistry] Unregistered id='{}'", id);
            persistence.delete(PERSISTENCE_TYPE, id);
        }
        return removed;
    }

    public Optional<WebhookDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public boolean exists(String id) {
        return definitions.containsKey(id);
    }

    public Collection<WebhookDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public int count() {
        return definitions.size();
    }
}
