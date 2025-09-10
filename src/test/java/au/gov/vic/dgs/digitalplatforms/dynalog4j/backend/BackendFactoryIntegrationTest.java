package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.junit.jupiter.api.Test;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.AppConfiguration;

import static org.assertj.core.api.Assertions.*;

/**
 * Simple integration tests for BackendFactory that test the default behavior
 * using the new configuration-based approach.
 */
class BackendFactoryIntegrationTest {

    @Test
    void testCreateBackendReturnsValidBackend() throws Exception {
        // Arrange - Create a default configuration
        AppConfiguration config = AppConfiguration.parse(new String[]{});

        // Act - BackendFactory should return a valid backend based on configuration
        Backend backend = BackendFactory.createBackend(config);

        // Assert - Should return some valid backend type
        assertThat(backend).isNotNull();
        assertThat(backend).isInstanceOfAny(EnvBackend.class, FileBackend.class, DynamoDBBackend.class);
    }

    @Test
    void testEnvBackendCreation() throws Exception {
        // Arrange
        AppConfiguration config = AppConfiguration.parse(new String[]{"--backend", "env"});

        // Act
        Backend backend = BackendFactory.createBackend(config);

        // Assert
        assertThat(backend).isNotNull();
        assertThat(backend).isInstanceOf(EnvBackend.class);
    }

    @Test
    void testFileBackendCreation() throws Exception {
        // Arrange
        AppConfiguration config = AppConfiguration.parse(new String[]{"--backend", "file", "--config-path", "/nonexistent/path/file.yaml"});

        // Act
        Backend backend = BackendFactory.createBackend(config);

        // Assert
        assertThat(backend).isNotNull();
        assertThat(backend).isInstanceOf(FileBackend.class);
    }

    @Test
    void testEnvBackendDoesNotThrowException() {
        // Act & Assert - EnvBackend should work without throwing exceptions
        assertThatCode(() -> {
            EnvBackend envBackend = new EnvBackend();
            envBackend.fetchDesiredLevels();
        }).doesNotThrowAnyException();
    }

    @Test
    void testFileBackendWithNonExistentFileReturnsEmptyMap() throws Exception {
        // Arrange
        FileBackend fileBackend = new FileBackend("/nonexistent/path/file.yaml");

        // Act
        var result = fileBackend.fetchDesiredLevels();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void testDynamoDBBackendCreationDirectly() {
        // Act & Assert - Should be able to create DynamoDB backend directly
        assertThatCode(() -> {
            new DynamoDBBackend("test-table", "test-service");
        }).doesNotThrowAnyException();
    }
}
