package au.gov.vic.dgs.digitalplatforms.dynalog4j.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ConfigurationReconcilerTest {

    private ConfigurationReconciler reconciler;
    private String basicLog4jConfig;

    @BeforeEach
    void setUp() {
        reconciler = new ConfigurationReconciler();
        basicLog4jConfig = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Configuration status="WARN">
                <Appenders>
                    <Console name="Console" target="SYSTEM_OUT">
                        <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
                    </Console>
                </Appenders>
                <Loggers>
                    <Root level="INFO">
                        <AppenderRef ref="Console"/>
                    </Root>
                </Loggers>
            </Configuration>
            """;
    }

    @Test
    void testReconcileConfigurationWithNewLogger() throws Exception {
        // Arrange
        Map<String, String> desiredLevels = Map.of("com.example.Service", "DEBUG");

        // Act
        String result = reconciler.reconcileConfiguration(basicLog4jConfig, desiredLevels);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("com.example.Service");
        assertThat(result).contains("DEBUG");
    }

    @Test
    void testReconcileConfigurationWithExistingRootLogger() throws Exception {
        // Arrange
        Map<String, String> desiredLevels = Map.of("ROOT", "DEBUG");

        // Act
        String result = reconciler.reconcileConfiguration(basicLog4jConfig, desiredLevels);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("Root level=\"DEBUG\"");
    }

    @Test
    void testReconcileConfigurationWithEmptyDesiredLevels() throws Exception {
        // Arrange
        Map<String, String> desiredLevels = new HashMap<>();

        // Act
        String result = reconciler.reconcileConfiguration(basicLog4jConfig, desiredLevels);

        // Assert
        assertThat(result).isNotNull();
        // XML transformer may add standalone="no" and change formatting, but content should be equivalent
        assertThat(result)
            .contains("<Configuration status=\"WARN\">")
            .contains("<Root level=\"INFO\">")
            .contains("<Console name=\"Console\"")
            .doesNotContain("<!--dynalog4j-override-->"); // Should have no dynamic loggers
    }

    @Test
    void testReconcileConfigurationWithMultipleLoggers() throws Exception {
        // Arrange
        Map<String, String> desiredLevels = Map.of(
            "com.example.Service", "DEBUG",
            "org.springframework", "WARN",
            "ROOT", "ERROR"
        );

        // Act
        String result = reconciler.reconcileConfiguration(basicLog4jConfig, desiredLevels);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("com.example.Service");
        assertThat(result).contains("org.springframework");
        assertThat(result).contains("Root level=\"ERROR\"");
    }

    @Test
    void testReconcileConfigurationThrowsExceptionForInvalidXml() {
        // Arrange
        String invalidXml = "<invalid xml content";
        Map<String, String> desiredLevels = Map.of("ROOT", "DEBUG");

        // Act & Assert
        assertThatThrownBy(() -> reconciler.reconcileConfiguration(invalidXml, desiredLevels))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testReconcileConfigurationThrowsExceptionForNullConfig() {
        // Arrange
        Map<String, String> desiredLevels = Map.of("ROOT", "DEBUG");

        // Act & Assert
        assertThatThrownBy(() -> reconciler.reconcileConfiguration(null, desiredLevels))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void testReconcileConfigurationThrowsExceptionForNullDesiredLevels() {
        // Act & Assert - Should throw NPE for null desiredLevels
        assertThatThrownBy(() -> {
            reconciler.reconcileConfiguration(basicLog4jConfig, null);
        }).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testRemoveDynamicLoggersWhenNoLongerDesired() throws Exception {
        // Arrange - First add some loggers via overrides
        Map<String, String> initialDesiredLevels = Map.of(
            "com.example.Service", "DEBUG",
            "au.com.objectconsulting.cbdm", "DEBUG"
        );
        
        String configWithOverrides = reconciler.reconcileConfiguration(basicLog4jConfig, initialDesiredLevels);
        assertThat(configWithOverrides).contains("com.example.Service");
        assertThat(configWithOverrides).contains("au.com.objectconsulting.cbdm");
        assertThat(configWithOverrides).contains("<!--dynalog4j-override-->");

        // Act - Remove one of the overrides
        Map<String, String> newDesiredLevels = Map.of("com.example.Service", "DEBUG");
        String updatedConfig = reconciler.reconcileConfiguration(configWithOverrides, newDesiredLevels);

        // Assert - The removed logger should be gone, the kept one should remain
        assertThat(updatedConfig).contains("com.example.Service");
        assertThat(updatedConfig).doesNotContain("au.com.objectconsulting.cbdm");
    }

    @Test
    void testRemoveAllDynamicLoggersWhenEmptyDesiredLevels() throws Exception {
        // Arrange - First add some loggers via overrides
        Map<String, String> initialDesiredLevels = Map.of(
            "com.example.Service", "DEBUG",
            "au.com.objectconsulting.cbdm", "DEBUG",
            "org.springframework", "INFO"
        );
        
        String configWithOverrides = reconciler.reconcileConfiguration(basicLog4jConfig, initialDesiredLevels);
        assertThat(configWithOverrides).contains("com.example.Service");
        assertThat(configWithOverrides).contains("au.com.objectconsulting.cbdm");
        assertThat(configWithOverrides).contains("org.springframework");

        // Act - Remove all overrides
        Map<String, String> emptyDesiredLevels = Map.of();
        String cleanedConfig = reconciler.reconcileConfiguration(configWithOverrides, emptyDesiredLevels);

        // Assert - All dynamic loggers should be removed, only Root should remain
        assertThat(cleanedConfig).doesNotContain("com.example.Service");
        assertThat(cleanedConfig).doesNotContain("au.com.objectconsulting.cbdm"); 
        assertThat(cleanedConfig).doesNotContain("org.springframework");
        assertThat(cleanedConfig).contains("<Root level=\"INFO\">");  // Original Root should remain
    }

    @Test
    void testPreserveStaticLoggersWhenRemovingDynamicOnes() throws Exception {
        // Arrange - Start with config that has a static logger (no comment marker)
        String configWithStaticLogger = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Configuration status="WARN">
                <Appenders>
                    <Console name="Console" target="SYSTEM_OUT">
                        <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
                    </Console>
                </Appenders>
                <Loggers>
                    <Logger name="org.apache.http" level="WARN"/>
                    <Root level="INFO">
                        <AppenderRef ref="Console"/>
                    </Root>
                </Loggers>
            </Configuration>
            """;

        // First add some dynamic loggers
        Map<String, String> initialDesiredLevels = Map.of(
            "com.example.Service", "DEBUG",
            "au.com.objectconsulting.cbdm", "DEBUG"
        );
        
        String configWithDynamicLoggers = reconciler.reconcileConfiguration(configWithStaticLogger, initialDesiredLevels);
        assertThat(configWithDynamicLoggers).contains("org.apache.http");  // Static logger
        assertThat(configWithDynamicLoggers).contains("com.example.Service");  // Dynamic logger
        assertThat(configWithDynamicLoggers).contains("au.com.objectconsulting.cbdm");  // Dynamic logger

        // Act - Remove all dynamic overrides
        Map<String, String> emptyDesiredLevels = Map.of();
        String cleanedConfig = reconciler.reconcileConfiguration(configWithDynamicLoggers, emptyDesiredLevels);

        // Assert - Static logger should remain, dynamic loggers should be removed
        assertThat(cleanedConfig).contains("org.apache.http");  // Static logger preserved
        assertThat(cleanedConfig).doesNotContain("com.example.Service");  // Dynamic logger removed
        assertThat(cleanedConfig).doesNotContain("au.com.objectconsulting.cbdm");  // Dynamic logger removed
        assertThat(cleanedConfig).contains("<Root level=\"INFO\">");  // Root preserved
    }

    @Test
    void testUpdateExistingStaticLoggerAndMarkAsDynamic() throws Exception {
        // Arrange - Start with config that has a static logger
        String configWithStaticLogger = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Configuration status="WARN">
                <Appenders>
                    <Console name="Console" target="SYSTEM_OUT">
                        <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
                    </Console>
                </Appenders>
                <Loggers>
                    <Logger name="com.example.Service" level="INFO"/>
                    <Root level="INFO">
                        <AppenderRef ref="Console"/>
                    </Root>
                </Loggers>
            </Configuration>
            """;

        // Act - Apply an override to the existing static logger
        Map<String, String> desiredLevels = Map.of("com.example.Service", "DEBUG");
        String updatedConfig = reconciler.reconcileConfiguration(configWithStaticLogger, desiredLevels);

        // Assert - Logger should be updated and marked as dynamic
        assertThat(updatedConfig).contains("com.example.Service");
        assertThat(updatedConfig).contains("level=\"DEBUG\"");
        assertThat(updatedConfig).contains("<!--dynalog4j-override-->");

        // Now remove the override
        Map<String, String> emptyDesiredLevels = Map.of();
        String cleanedConfig = reconciler.reconcileConfiguration(updatedConfig, emptyDesiredLevels);

        // The logger should be removed since it's now marked as dynamic
        assertThat(cleanedConfig).doesNotContain("com.example.Service");
    }
}
