package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(SystemStubsExtension.class)
class EnvBackendTestSimple {

    @SystemStub
    private EnvironmentVariables environment;
    
    private EnvBackend envBackend;

    @BeforeEach
    void setUp() {
        envBackend = new EnvBackend();
    }

    @Test
    void testFetchDesiredLevelsWithValidEnvironmentVariables() throws Exception {
        // Arrange
        environment.set("LOG_LEVEL_com.example.Class1", "DEBUG");
        environment.set("LOG_LEVEL_com.example.Class2", "info"); // Should be uppercased
        environment.set("LOG_LEVEL_ROOT", "WARN");
        environment.set("OTHER_ENV_VAR", "should_be_ignored");

        // Act
        Map<String, String> result = envBackend.fetchDesiredLevels();
        
        // Assert
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get("com.example.Class1")).isEqualTo("DEBUG");
        assertThat(result.get("com.example.Class2")).isEqualTo("INFO"); // Should be uppercase
        assertThat(result.get("ROOT")).isEqualTo("WARN");
        assertThat(result.get("OTHER_ENV_VAR")).isNull();
    }

    @Test
    void testFetchDesiredLevelsWithInvalidLogLevels() throws Exception {
        // Arrange
        environment.set("LOG_LEVEL_com.example.Valid", "DEBUG");
        environment.set("LOG_LEVEL_com.example.Invalid", "INVALID_LEVEL");
        environment.set("LOG_LEVEL_com.example.Empty", "");

        // Act
        Map<String, String> result = envBackend.fetchDesiredLevels();
        
        // Assert
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get("com.example.Valid")).isEqualTo("DEBUG");
        assertThat(result.get("com.example.Invalid")).isNull();
        assertThat(result.get("com.example.Empty")).isNull();
    }

    @Test
    void testFetchDesiredLevelsWithNoLogLevelEnvironmentVariables() throws Exception {
        // Arrange
        environment.set("SOME_OTHER_VAR", "value");
        environment.set("NOT_LOG_LEVEL", "DEBUG");

        // Act
        Map<String, String> result = envBackend.fetchDesiredLevels();
        
        // Assert
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void testFetchDesiredLevelsDoesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            Map<String, String> result = envBackend.fetchDesiredLevels();
            assertThat(result).isNotNull();
        });
    }
}
