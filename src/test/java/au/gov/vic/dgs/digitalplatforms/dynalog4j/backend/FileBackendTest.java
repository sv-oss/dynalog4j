package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileBackendTest {

    @TempDir
    Path tempDir;

    private FileBackend fileBackend;
    private Path configFile;

    @BeforeEach
    void setUp() {
        configFile = tempDir.resolve("log-levels.yaml");
    }

    @Test
    void testFetchDesiredLevelsFromValidYamlFile() throws Exception {
        // Arrange
        String yamlContent = """
            loggers:
              com.example.Class1: DEBUG
              com.example.Class2: INFO
              ROOT: WARN
              org.springframework.web: ERROR
            """;
        Files.writeString(configFile, yamlContent);
        fileBackend = new FileBackend(configFile.toString());

        // Act
        Map<String, String> result = fileBackend.fetchDesiredLevels();

        // Assert
        assertEquals(4, result.size());
        assertEquals("DEBUG", result.get("com.example.Class1"));
        assertEquals("INFO", result.get("com.example.Class2"));
        assertEquals("WARN", result.get("ROOT"));
        assertEquals("ERROR", result.get("org.springframework.web"));
    }

    @Test
    void testFetchDesiredLevelsFromValidJsonFile() throws Exception {
        // Arrange
        Path jsonFile = tempDir.resolve("log-levels.json");
        String jsonContent = """
            {
              "loggers": {
                "com.example.Service": "DEBUG",
                "ROOT": "INFO"
              }
            }
            """;
        Files.writeString(jsonFile, jsonContent);
        fileBackend = new FileBackend(jsonFile.toString());

        // Act
        Map<String, String> result = fileBackend.fetchDesiredLevels();

        // Assert
        assertEquals(2, result.size());
        assertEquals("DEBUG", result.get("com.example.Service"));
        assertEquals("INFO", result.get("ROOT"));
    }

    @Test
    void testFetchDesiredLevelsFromNonExistentFile() throws Exception {
        // Arrange
        Path nonExistentFile = tempDir.resolve("does-not-exist.yaml");
        fileBackend = new FileBackend(nonExistentFile.toString());

        // Act
        Map<String, String> result = fileBackend.fetchDesiredLevels();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchDesiredLevelsFromEmptyFile() throws Exception {
        // Arrange
        Files.writeString(configFile, "");
        fileBackend = new FileBackend(configFile.toString());

        // Act
        Map<String, String> result = fileBackend.fetchDesiredLevels();

        // Assert - empty file should return empty map, not throw exception
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchDesiredLevelsFromInvalidYaml() throws Exception {
        // Arrange
        String invalidYaml = """
            loggers:
              com.example.Class1: DEBUG
              invalid_structure: [
            """;
        Files.writeString(configFile, invalidYaml);
        fileBackend = new FileBackend(configFile.toString());

        // Act & Assert
        assertThrows(Exception.class, () -> fileBackend.fetchDesiredLevels());
    }

    @Test
    void testFetchDesiredLevelsFromInvalidJson() throws Exception {
        // Arrange
        Path jsonFile = tempDir.resolve("log-levels.json");
        String invalidJson = """
            {
              "loggers": {
                "com.example.Service": "DEBUG",
                "invalid": 
              }
            }
            """;
        Files.writeString(jsonFile, invalidJson);
        fileBackend = new FileBackend(jsonFile.toString());

        // Act & Assert
        assertThrows(Exception.class, () -> fileBackend.fetchDesiredLevels());
    }

    @Test
    void testFetchDesiredLevelsWithMissingLoggersSection() throws Exception {
        // Arrange
        String yamlContent = """
            some_other_section:
              value: test
            """;
        Files.writeString(configFile, yamlContent);
        fileBackend = new FileBackend(configFile.toString());

        // Act
        Map<String, String> result = fileBackend.fetchDesiredLevels();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchDesiredLevelsWithEmptyLoggersSection() throws Exception {
        // Arrange
        String yamlContent = """
            loggers:
            """;
        Files.writeString(configFile, yamlContent);
        fileBackend = new FileBackend(configFile.toString());

        // Act
        Map<String, String> result = fileBackend.fetchDesiredLevels();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchDesiredLevelsWithInvalidLogLevels() throws Exception {
        // Arrange
        String yamlContent = """
            loggers:
              com.example.Valid: DEBUG
              com.example.Invalid: INVALID_LEVEL
              com.example.Empty: ""
              com.example.Number: 123
            """;
        Files.writeString(configFile, yamlContent);
        fileBackend = new FileBackend(configFile.toString());

        // Act
        Map<String, String> result = fileBackend.fetchDesiredLevels();

        // Assert
        assertEquals(1, result.size()); // Should only accept valid log level
        assertEquals("DEBUG", result.get("com.example.Valid"));
        assertNull(result.get("com.example.Invalid"));
        assertNull(result.get("com.example.Empty"));
        assertNull(result.get("com.example.Number")); // Numbers are invalid log levels
    }

    @Test
    void testFetchDesiredLevelsWithComplexLoggerNames() throws Exception {
        // Arrange
        String yamlContent = """
            loggers:
              com.company.module.submodule.ClassName: DEBUG
              org.springframework.web.servlet.DispatcherServlet: WARN
              ROOT: ERROR
              "": INFO
            """;
        Files.writeString(configFile, yamlContent);
        fileBackend = new FileBackend(configFile.toString());

        // Act
        Map<String, String> result = fileBackend.fetchDesiredLevels();

        // Assert
        assertEquals(4, result.size());
        assertEquals("DEBUG", result.get("com.company.module.submodule.ClassName"));
        assertEquals("WARN", result.get("org.springframework.web.servlet.DispatcherServlet"));
        assertEquals("ERROR", result.get("ROOT"));
        assertEquals("INFO", result.get(""));
    }

    @Test
    void testDefaultConstructorUsesEnvironmentVariable() {
        // Arrange & Act
        FileBackend defaultBackend = new FileBackend();

        // Assert - Just verify it doesn't throw an exception
        assertDoesNotThrow(() -> {
            Map<String, String> result = defaultBackend.fetchDesiredLevels();
            assertNotNull(result);
        });
    }
}
