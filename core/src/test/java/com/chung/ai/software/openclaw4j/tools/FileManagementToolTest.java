package com.chung.ai.software.openclaw4j.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FileManagementToolTest {

    @Autowired
    private FileManagementTool fileManagementTool;

    private final String testDir = "test_folder_management";

    @BeforeEach
    void setUp() throws IOException {
        Path path = Paths.get(testDir);
        if (Files.exists(path)) {
            fileManagementTool.deleteFile(testDir);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        Path path = Paths.get(testDir);
        if (Files.exists(path)) {
            fileManagementTool.deleteFile(testDir);
        }
        Path renamedPath = Paths.get(testDir + "_renamed");
        if (Files.exists(renamedPath)) {
            fileManagementTool.deleteFile(testDir + "_renamed");
        }
    }

    @Test
    void testCreateDirectory() {
        String result = fileManagementTool.createDirectory(testDir);
        assertTrue(result.contains("Successfully created"));
        assertTrue(Files.exists(Paths.get(testDir)));
        assertTrue(Files.isDirectory(Paths.get(testDir)));
    }

    @Test
    void testDeleteDirectory() {
        fileManagementTool.createDirectory(testDir);
        String result = fileManagementTool.deleteFile(testDir);
        assertTrue(result.contains("Successfully deleted"));
        assertFalse(Files.exists(Paths.get(testDir)));
    }

    @Test
    void testRenameDirectory() {
        fileManagementTool.createDirectory(testDir);
        String newName = testDir + "_renamed";
        String result = fileManagementTool.rename(testDir, newName);
        assertTrue(result.contains("Successfully renamed"));
        assertFalse(Files.exists(Paths.get(testDir)));
        assertTrue(Files.exists(Paths.get(newName)));
    }
    @Test
    void testCreateFolder() {
        String result = fileManagementTool.createFolder(testDir);
        assertTrue(result.contains("Successfully created"));
        assertTrue(Files.exists(Paths.get(testDir)));
        assertTrue(Files.isDirectory(Paths.get(testDir)));
    }

    @Test
    void testDeleteFolder() {
        fileManagementTool.createFolder(testDir);
        String result = fileManagementTool.deleteFolder(testDir);
        assertTrue(result.contains("Successfully deleted"));
        assertFalse(Files.exists(Paths.get(testDir)));
    }

    @Test
    void testRenameFolder() {
        fileManagementTool.createFolder(testDir);
        String newName = testDir + "_renamed";
        String result = fileManagementTool.renameFolder(testDir, newName);
        assertTrue(result.contains("Successfully renamed"));
        assertFalse(Files.exists(Paths.get(testDir)));
        assertTrue(Files.exists(Paths.get(newName)));
    }
}
