package com.chung.ai.software.openclaw4j.gateway.hook.bundled;

import com.chung.ai.software.openclaw4j.gateway.hook.HookDefinition;
import com.chung.ai.software.openclaw4j.gateway.hook.HookEventType;
import com.chung.ai.software.openclaw4j.gateway.hook.HookRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Bundled hook that persists a lightweight memory snapshot as a Markdown file
 * under {@code ~/.openclaw/memory/} whenever the user issues /new.
 *
 * Registered as ENABLED by default so snapshots are captured out of the box
 * without any operator configuration.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SessionMemoryHook {

    private final HookRegistry hookRegistry;

    @PostConstruct
    public void registerHook() {
        hookRegistry.register(HookDefinition.builder()
                .name("session-memory")
                .description("Saves a Markdown memory snapshot to ~/.openclaw/memory/ on /new")
                .eventType(HookEventType.COMMAND_NEW)
                .handler(context -> {
                    String safeTs = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                            .withZone(ZoneOffset.UTC)
                            .format(context.getTimestamp());
                    String fileName = context.getSessionId() + "-" + safeTs + ".md";
                    Path snapshotFile = Paths.get(
                            System.getProperty("user.home"), ".openclaw4j", "memory", fileName);

                    String payload = (context.getPayload() == null || context.getPayload().isBlank())
                            ? "(none)"
                            : context.getPayload();

                    String content = "# Session Memory\n\n"
                            + "Session: " + context.getSessionId() + "\n"
                            + "Saved at: " + context.getTimestamp() + "\n"
                            + "Event: COMMAND_NEW\n"
                            + "Payload: " + payload + "\n";

                    try {
                        Files.createDirectories(snapshotFile.getParent());
                        Files.writeString(snapshotFile, content);
                    } catch (IOException e) {
                        log.warn("[SessionMemoryHook] Failed to write memory snapshot '{}': {}",
                                snapshotFile, e.getMessage());
                    }
                })
                .enabled(true)
                .build());
    }
}
