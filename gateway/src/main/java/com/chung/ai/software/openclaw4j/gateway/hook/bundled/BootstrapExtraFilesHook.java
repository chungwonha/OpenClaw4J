package com.chung.ai.software.openclaw4j.gateway.hook.bundled;

import com.chung.ai.software.openclaw4j.gateway.hook.HookDefinition;
import com.chung.ai.software.openclaw4j.gateway.hook.HookEventType;
import com.chung.ai.software.openclaw4j.gateway.hook.HookRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Bundled hook that scans {@code ./hooks/bootstrap-extra-files/} for Markdown
 * and text files when an agent is bootstrapped for the first time.
 *
 * This is a placeholder for future injection logic: when enabled, the hook
 * discovers the files and sets a result string, but does not yet inject their
 * contents into the agent prompt.
 *
 * Registered as DISABLED by default.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BootstrapExtraFilesHook {

    private final HookRegistry hookRegistry;

    @PostConstruct
    public void registerHook() {
        hookRegistry.register(HookDefinition.builder()
                .name("bootstrap-extra-files")
                .description("Scans ./hooks/bootstrap-extra-files/ for .md/.txt files on agent bootstrap")
                .eventType(HookEventType.AGENT_BOOTSTRAP)
                .handler(context -> {
                    File extraFilesDir = new File("hooks/bootstrap-extra-files");
                    if (!extraFilesDir.exists() || !extraFilesDir.isDirectory()) {
                        log.debug("[BootstrapExtraFilesHook] Directory '{}' not found — skipping",
                                extraFilesDir.getAbsolutePath());
                        return;
                    }

                    File[] allFiles = extraFilesDir.listFiles();
                    if (allFiles == null) {
                        log.debug("[BootstrapExtraFilesHook] Directory '{}' could not be listed",
                                extraFilesDir.getAbsolutePath());
                        return;
                    }

                    List<File> extraFiles = Arrays.stream(allFiles)
                            .filter(f -> f.isFile() && (f.getName().endsWith(".md") || f.getName().endsWith(".txt")))
                            .toList();

                    int count = extraFiles.size();
                    extraFiles.forEach(f -> log.info("[BootstrapExtraFilesHook] Found extra file: {}", f.getName()));

                    context.setResult("Found " + count + " extra bootstrap files");
                })
                .enabled(false)
                .build());
    }
}
