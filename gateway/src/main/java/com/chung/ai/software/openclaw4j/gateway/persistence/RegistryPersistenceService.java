package com.chung.ai.software.openclaw4j.gateway.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Persists registry entries as individual JSON files under ~/.openclaw4j/registry/{type}/.
 * Each entry is stored as {id}.json so updates are atomic at the file level.
 */
@Component
@Slf4j
public class RegistryPersistenceService {

    private final ObjectMapper mapper;
    private final Path baseDir;

    public RegistryPersistenceService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.baseDir = Path.of(System.getProperty("user.home"), ".openclaw4j", "registry");
    }

    public <T> void save(String type, String id, T definition) {
        try {
            Path dir = baseDir.resolve(type);
            Files.createDirectories(dir);
            File file = dir.resolve(id + ".json").toFile();
            mapper.writeValue(file, definition);
            log.debug("[Persistence] Saved {}/{}.json", type, id);
        } catch (IOException e) {
            log.error("[Persistence] Failed to save {}/{}", type, id, e);
        }
    }

    public <T> List<T> loadAll(String type, Class<T> clazz) {
        List<T> results = new ArrayList<>();
        Path dir = baseDir.resolve(type);
        if (!Files.exists(dir)) return results;
        File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return results;
        Arrays.sort(files); // deterministic ordering
        for (File file : files) {
            try {
                results.add(mapper.readValue(file, clazz));
                log.debug("[Persistence] Loaded {}/{}", type, file.getName());
            } catch (IOException e) {
                log.error("[Persistence] Failed to load {}/{} — skipping", type, file.getName(), e);
            }
        }
        return results;
    }

    public void delete(String type, String id) {
        try {
            Path file = baseDir.resolve(type).resolve(id + ".json");
            Files.deleteIfExists(file);
            log.debug("[Persistence] Deleted {}/{}.json", type, id);
        } catch (IOException e) {
            log.error("[Persistence] Failed to delete {}/{}", type, id, e);
        }
    }
}
