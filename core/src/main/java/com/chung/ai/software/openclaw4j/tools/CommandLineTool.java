package com.chung.ai.software.openclaw4j.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommandLineTool {

    @Tool("Executes an OS command and returns its output (stdout and stderr).")
    public String executeCommand(String command) {
        log.info("Executing OS command: {}", command);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            // Use different command execution for Windows vs Unix
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String output = reader.lines().collect(Collectors.joining("\n"));
                
                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return "Error: Command timed out after 30 seconds.\nPartial output:\n" + output;
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    return "Command failed with exit code " + exitCode + ".\nOutput:\n" + output;
                }

                return output;
            }
        } catch (Exception e) {
            log.error("Error executing command {}: {}", command, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
