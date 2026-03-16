package com.chung.ai.software.openclaw4j.gateway.hook.bundled;

import com.chung.ai.software.openclaw4j.gateway.hook.HookDefinition;
import com.chung.ai.software.openclaw4j.gateway.hook.HookEventType;
import com.chung.ai.software.openclaw4j.gateway.hook.HookHandler;
import com.chung.ai.software.openclaw4j.gateway.hook.HookRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Bundled hook that appends a line to {@code ~/.openclaw4j/command-log.txt}
 * whenever the user issues /new, /reset, or /stop.
 *
 * Registered as DISABLED by default so it must be explicitly enabled via
 * {@code PUT /api/hooks/command-logger-new/enable} (and the other two names)
 * before it produces any output.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommandLoggerHook {

    private final HookRegistry hookRegistry;

    @PostConstruct
    public void registerHooks() {
        HookHandler commandLogger = context -> {
            String line = "[" + context.getTimestamp() + "]"
                    + " session=" + context.getSessionId()
                    + " event=" + context.getEventType().name()
                    + " payload=" + context.getPayload()
                    + "\n";

            Path logFile = Paths.get(System.getProperty("user.home"), ".openclaw4j", "command-log.txt");
            try {
                Files.createDirectories(logFile.getParent());
                Files.writeString(logFile, line,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warn("[CommandLoggerHook] Failed to write command log: {}", e.getMessage());
            }
        };

        hookRegistry.register(HookDefinition.builder()
                .name("command-logger-new")
                .description("Logs /new command invocations to ~/.openclaw4j/command-log.txt")
                .eventType(HookEventType.COMMAND_NEW)
                .handler(commandLogger)
                .enabled(false)
                .build());

        hookRegistry.register(HookDefinition.builder()
                .name("command-logger-reset")
                .description("Logs /reset command invocations to ~/.openclaw4j/command-log.txt")
                .eventType(HookEventType.COMMAND_RESET)
                .handler(commandLogger)
                .enabled(false)
                .build());

        hookRegistry.register(HookDefinition.builder()
                .name("command-logger-stop")
                .description("Logs /stop command invocations to ~/.openclaw4j/command-log.txt")
                .eventType(HookEventType.COMMAND_STOP)
                .handler(commandLogger)
                .enabled(false)
                .build());
    }
}
