package com.chung.ai.software.mycalw2.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CommandLineToolTest {

    @Autowired
    private CommandLineTool commandLineTool;

    @Test
    void testExecuteEcho() {
        String command = "echo Hello World";
        String result = commandLineTool.executeCommand(command);
        assertTrue(result.contains("Hello World"), "Output should contain 'Hello World'. Got: " + result);
    }

    @Test
    void testExecuteInvalidCommand() {
        String command = "invalidcommandthatdoesnotexist";
        String result = commandLineTool.executeCommand(command);
        assertTrue(result.contains("Error") || result.contains("not recognized"), "Should return an error message. Got: " + result);
    }

    @Test
    void testExecuteDirOrLs() {
        String command = System.getProperty("os.name").toLowerCase().contains("win") ? "dir" : "ls";
        String result = commandLineTool.executeCommand(command);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
