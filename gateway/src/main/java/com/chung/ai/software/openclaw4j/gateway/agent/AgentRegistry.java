package com.chung.ai.software.openclaw4j.gateway.agent;

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
 * Central registry of named agent definitions.
 *
 * Lifecycle
 * ---------
 * 1. Gateway starts → AgentRegistry is created → "default" agent is pre-registered.
 * 2. Additional agents can be registered at any time via REST (AgentManagementController)
 *    or programmatically (e.g. from a config file on startup).
 * 3. When a new AgentSession is created, it holds a reference to this registry.
 *    On the first message routed to a named agent, the session calls
 *    ChatAgentFactory.createChatAgent(name, definition.description) to create a
 *    per-session instance with its own isolated memory window.
 *
 * Thread safety: ConcurrentHashMap — safe for concurrent reads and writes.
 */
@Component
@Slf4j
public class AgentRegistry {

    public static final String DEFAULT_AGENT = "default";

    private static final String PERSISTENCE_TYPE = "agents";

    private final ConcurrentHashMap<String, AgentDefinition> definitions = new ConcurrentHashMap<>();
    private final RegistryPersistenceService persistence;

    public AgentRegistry(RegistryPersistenceService persistence) {
        this.persistence = persistence;
        // Pre-register the default general-purpose agent.
        // Sessions use this unless the user explicitly switches with /use <name>.
        register(AgentDefinition.builder()
                .name(DEFAULT_AGENT)
                .description("General-purpose AI assistant available through Microsoft Teams. " +
                             "Be concise, helpful, and professional. " +
                             "When you don't know something, say so rather than guessing.")
                .build());

        log.info("[AgentRegistry] Initialized with default agent");
    }

    @PostConstruct
    public void loadPersistedDefinitions() {
        List<AgentDefinition> saved = persistence.loadAll(PERSISTENCE_TYPE, AgentDefinition.class);
        for (AgentDefinition definition : saved) {
            // Skip the default agent — it is already registered above with canonical description
            if (DEFAULT_AGENT.equals(definition.getName())) continue;
            definitions.put(definition.getName(), definition);
            log.info("[AgentRegistry] Restored agent='{}' from persistence", definition.getName());
        }
        log.info("[AgentRegistry] Loaded {} persisted agent(s)", saved.size());
    }

    /**
     * Register a new agent definition (or overwrite an existing one by the same name).
     * All sessions that subsequently route a message to {@code definition.name} will
     * get an instance of this agent.
     */
    public AgentDefinition register(AgentDefinition definition) {
        definitions.put(definition.getName(), definition);
        log.info("[AgentRegistry] Agent registered: name='{}', description='{}'",
                definition.getName(), definition.getDescription());
        persistence.save(PERSISTENCE_TYPE, definition.getName(), definition);
        return definition;
    }

    /** Remove a definition. Sessions that already have an instance keep it until evicted. */
    public boolean unregister(String name) {
        if (DEFAULT_AGENT.equals(name)) {
            log.warn("[AgentRegistry] Cannot unregister the default agent");
            return false;
        }
        boolean removed = definitions.remove(name) != null;
        if (removed) {
            log.info("[AgentRegistry] Agent unregistered: '{}'", name);
            persistence.delete(PERSISTENCE_TYPE, name);
        }
        return removed;
    }

    public Optional<AgentDefinition> get(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    public boolean exists(String name) {
        return definitions.containsKey(name);
    }

    public Collection<AgentDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public int count() {
        return definitions.size();
    }
}
