package com.chung.ai.software.openclaw4j.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
public class McpRegistry {
    private static final String CONFIG_FILE = "config/mcp-servers.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final List<McpServerConfig> servers = new ArrayList<>();
    private final List<Consumer<McpServerConfig>> addListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> removeListeners = new CopyOnWriteArrayList<>();
    private final Consumer<List<McpServerConfig>> onUpdate;

    public McpRegistry(List<McpServerConfig> initialServers, Consumer<List<McpServerConfig>> onUpdate) {
        if (initialServers != null) {
            this.servers.addAll(initialServers);
        }
        this.onUpdate = onUpdate;
    }

    private void load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try {
                List<McpServerConfig> loaded = MAPPER.readValue(file, new TypeReference<>() {});
                servers.addAll(loaded);
                log.info("Loaded {} MCP servers from persistence", servers.size());
            } catch (IOException e) {
                log.error("Failed to load MCP servers from {}", CONFIG_FILE, e);
            }
        }
    }

    private void save() {
        if (onUpdate != null) {
            onUpdate.accept(list());
        }
    }

    public void add(McpServerConfig config) {
        // Remove existing with same name if any
        remove(config.getName());
        servers.add(config);
        save();
        addListeners.forEach(l -> l.accept(config));
    }

    public void remove(String name) {
        if (servers.removeIf(s -> s.getName().equals(name))) {
            save();
            removeListeners.forEach(l -> l.accept(name));
        }
    }

    public void onAdd(Consumer<McpServerConfig> listener) {
        addListeners.add(listener);
    }

    public void onRemove(Consumer<String> listener) {
        removeListeners.add(listener);
    }

    public List<McpServerConfig> list() {
        return new ArrayList<>(servers);
    }

    public Optional<McpServerConfig> get(String name) {
        return servers.stream().filter(s -> s.getName().equals(name)).findFirst();
    }
}
