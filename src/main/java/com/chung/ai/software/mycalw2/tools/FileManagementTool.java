package com.chung.ai.software.mycalw2.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileManagementTool {

    private final Path rootPath = Paths.get("").toAbsolutePath();

    /**
     * Lists all files and directories in a given path relative to the project root.
     * @param relativePath The relative path to list contents for. Use "." for project root.
     * @return List of file and directory names.
     */
    @Tool("Lists files and directories in the specified path relative to the project root")
    public List<String> listFiles(String relativePath) {
        log.info("Listing files in: {}", relativePath);
        Path targetPath = rootPath.resolve(relativePath).normalize();
        
        if (!Files.exists(targetPath)) {
            log.warn("Path does not exist: {}", targetPath);
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(targetPath)) {
            return stream.map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error listing files in {}: {}", relativePath, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Reads the content of a file.
     * @param relativePath The relative path of the file to read.
     * @return The content of the file as a String.
     */
    @Tool("Reads the content of a file from the specified path relative to the project root")
    public String readFile(String relativePath) {
        log.info("Reading file: {}", relativePath);
        Path targetPath = rootPath.resolve(relativePath).normalize();

        if (!Files.exists(targetPath)) {
            log.warn("File does not exist: {}", targetPath);
            return "Error: File not found.";
        }

        try {
            return Files.readString(targetPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error reading file {}: {}", relativePath, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Writes content to a file. Overwrites the file if it already exists.
     * @param relativePath The relative path of the file to write to.
     * @param content The content to write.
     * @return Success or error message.
     */
    @Tool("Writes content to a file at the specified path relative to the project root. Overwrites if exists.")
    public String writeFile(String relativePath, String content) {
        log.info("Writing to file: {}", relativePath);
        Path targetPath = rootPath.resolve(relativePath).normalize();

        try {
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, content, StandardCharsets.UTF_8);
            return "Successfully written to " + relativePath;
        } catch (IOException e) {
            log.error("Error writing to file {}: {}", relativePath, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Appends content to an existing file.
     * @param relativePath The relative path of the file to append to.
     * @param content The content to append.
     * @return Success or error message.
     */
    @Tool("Appends content to a file at the specified path relative to the project root.")
    public String appendToFile(String relativePath, String content) {
        log.info("Appending to file: {}", relativePath);
        Path targetPath = rootPath.resolve(relativePath).normalize();

        try {
            Files.writeString(targetPath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Successfully appended to " + relativePath;
        } catch (IOException e) {
            log.error("Error appending to file {}: {}", relativePath, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Deletes a file or directory.
     * @param relativePath The relative path of the file or directory to delete.
     * @return Success or error message.
     */
    @Tool("Deletes a file or directory at the specified path relative to the project root.")
    public String deleteFile(String relativePath) {
        log.info("Deleting: {}", relativePath);
        Path targetPath = rootPath.resolve(relativePath).normalize();

        try {
            if (Files.isDirectory(targetPath)) {
                // Simplified recursive deletion
                try (Stream<Path> walk = Files.walk(targetPath)) {
                    List<Path> paths = walk.sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList());
                    for (Path p : paths) {
                        Files.delete(p);
                    }
                }
            } else {
                Files.deleteIfExists(targetPath);
            }
            return "Successfully deleted " + relativePath;
        } catch (IOException e) {
            log.error("Error deleting {}: {}", relativePath, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Creates a new directory.
     * @param relativePath The relative path of the directory to create.
     * @return Success or error message.
     */
    @Tool("Creates a new directory at the specified path relative to the project root.")
    public String createDirectory(String relativePath) {
        log.info("Creating directory: {}", relativePath);
        Path targetPath = rootPath.resolve(relativePath).normalize();

        try {
            Files.createDirectories(targetPath);
            return "Successfully created directory " + relativePath;
        } catch (IOException e) {
            log.error("Error creating directory {}: {}", relativePath, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Renames or moves a file or directory.
     * @param oldRelativePath The current relative path of the file or directory.
     * @param newRelativePath The new relative path.
     * @return Success or error message.
     */
    @Tool("Renames or moves a file or directory from one path to another relative to the project root.")
    public String rename(String oldRelativePath, String newRelativePath) {
        log.info("Renaming from {} to {}", oldRelativePath, newRelativePath);
        Path sourcePath = rootPath.resolve(oldRelativePath).normalize();
        Path targetPath = rootPath.resolve(newRelativePath).normalize();

        try {
            Files.createDirectories(targetPath.getParent());
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return "Successfully renamed " + oldRelativePath + " to " + newRelativePath;
        } catch (IOException e) {
            log.error("Error renaming {} to {}: {}", oldRelativePath, newRelativePath, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Creates a new folder.
     * @param relativePath The relative path of the folder to create.
     * @return Success or error message.
     */
    @Tool("Creates a new folder at the specified path relative to the project root.")
    public String createFolder(String relativePath) {
        return createDirectory(relativePath);
    }

    /**
     * Deletes a folder and all its contents.
     * @param relativePath The relative path of the folder to delete.
     * @return Success or error message.
     */
    @Tool("Deletes a folder and all its contents at the specified path relative to the project root.")
    public String deleteFolder(String relativePath) {
        return deleteFile(relativePath);
    }

    /**
     * Renames or moves a folder.
     * @param oldRelativePath The current relative path of the folder.
     * @param newRelativePath The new relative path.
     * @return Success or error message.
     */
    @Tool("Renames or moves a folder from one path to another relative to the project root.")
    public String renameFolder(String oldRelativePath, String newRelativePath) {
        return rename(oldRelativePath, newRelativePath);
    }

    /**
     * Checks if a file or directory exists.
     * @param relativePath The relative path to check.
     * @return True if exists, false otherwise.
     */
    @Tool("Checks if a file or directory exists at the specified path relative to the project root.")
    public boolean exists(String relativePath) {
        log.info("Checking existence of: {}", relativePath);
        Path targetPath = rootPath.resolve(relativePath).normalize();
        return Files.exists(targetPath);
    }
}
