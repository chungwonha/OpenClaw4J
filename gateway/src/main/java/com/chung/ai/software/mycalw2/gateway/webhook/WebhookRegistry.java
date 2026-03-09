package com.chung.ai.software.mycalw2.gateway.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
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

    private final ConcurrentHashMap<String, WebhookDefinition> definitions = new ConcurrentHashMap<>();

    public WebhookDefinition register(WebhookDefinition definition) {
        definitions.put(definition.getId(), definition);
        log.info("[WebhookRegistry] Registered id='{}' name='{}' agent='{}' output='{}'",
                definition.getId(), definition.getName(),
                definition.getAgentName(), definition.getOutputTarget());
        return definition;
    }

    public boolean unregister(String id) {
        boolean removed = definitions.remove(id) != null;
        if (removed) {
            log.info("[WebhookRegistry] Unregistered id='{}'", id);
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
