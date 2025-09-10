package au.gov.vic.dgs.digitalplatforms.dynalog4j.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;

@ExtendWith(SystemStubsExtension.class)
class AppConfigurationTest {

    @SystemStub
    private EnvironmentVariables environmentVariables;

    @BeforeEach
    void setUp() {
        // Clear any existing environment variables that might affect tests
        environmentVariables.set("BACKEND", null);
        environmentVariables.set("DYNAMO_TABLE_NAME", null);
        environmentVariables.set("SERVICE_NAME", null);
        environmentVariables.set("LOG_CONFIG_PATH", null);
        environmentVariables.set("JMX_HOST", null);
        environmentVariables.set("JMX_PORT", null);
        environmentVariables.set("TARGET_LOGGER_CONTEXT", null);
        environmentVariables.set("RECONCILE_INTERVAL_SECONDS", null);
        environmentVariables.set("DRY_RUN", null);
        environmentVariables.set("VERBOSE", null);
    }

    @Test
    void shouldUseDefaultValues() {
        AppConfiguration config = AppConfiguration.parse(new String[]{});
        
        assertThat(config.getBackend()).isEqualTo("env");
        assertThat(config.getDynamoTableName()).isEqualTo("log-levels");
        assertThat(config.getServiceName()).isEqualTo("default");
        assertThat(config.getConfigPath()).isEqualTo("/config/log-levels.yaml");
        assertThat(config.getJmxHost()).isEqualTo("localhost");
        assertThat(config.getJmxPort()).isEqualTo("9999");
        assertThat(config.getJmxUrl()).isEqualTo("service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi");
        assertThat(config.getTargetLoggerContext()).isNull();
        assertThat(config.getReconcileInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.isDryRun()).isFalse();
        assertThat(config.isVerbose()).isFalse();
    }

    @Test
    void shouldReadFromEnvironmentVariables() {
        environmentVariables.set("BACKEND", "file");
        environmentVariables.set("DYNAMO_TABLE_NAME", "custom-table");
        environmentVariables.set("SERVICE_NAME", "my-service");
        environmentVariables.set("LOG_CONFIG_PATH", "/custom/path.yaml");
        environmentVariables.set("JMX_HOST", "remote-host");
        environmentVariables.set("JMX_PORT", "8888");
        environmentVariables.set("TARGET_LOGGER_CONTEXT", "MyContext");
        environmentVariables.set("RECONCILE_INTERVAL_SECONDS", "60");
        environmentVariables.set("DRY_RUN", "true");
        environmentVariables.set("VERBOSE", "true");

        AppConfiguration config = AppConfiguration.parse(new String[]{});
        
        assertThat(config.getBackend()).isEqualTo("file");
        assertThat(config.getDynamoTableName()).isEqualTo("custom-table");
        assertThat(config.getServiceName()).isEqualTo("my-service");
        assertThat(config.getConfigPath()).isEqualTo("/custom/path.yaml");
        assertThat(config.getJmxHost()).isEqualTo("remote-host");
        assertThat(config.getJmxPort()).isEqualTo("8888");
        assertThat(config.getJmxUrl()).isEqualTo("service:jmx:rmi:///jndi/rmi://remote-host:8888/jmxrmi");
        assertThat(config.getTargetLoggerContext()).isEqualTo("MyContext");
        assertThat(config.getReconcileInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.isDryRun()).isTrue();
        assertThat(config.isVerbose()).isTrue();
    }

    @Test
    void shouldPreferCliArgumentsOverEnvironmentVariables() {
        environmentVariables.set("BACKEND", "file");
        environmentVariables.set("JMX_HOST", "env-host");
        environmentVariables.set("RECONCILE_INTERVAL_SECONDS", "120");

        AppConfiguration config = AppConfiguration.parse(new String[]{
            "--backend", "dynamo",
            "--jmx-host", "cli-host",
            "--interval", "45",
            "--dry-run",
            "--verbose"
        });
        
        assertThat(config.getBackend()).isEqualTo("dynamo");
        assertThat(config.getJmxHost()).isEqualTo("cli-host");
        assertThat(config.getReconcileInterval()).isEqualTo(Duration.ofSeconds(45));
        assertThat(config.isDryRun()).isTrue();
        assertThat(config.isVerbose()).isTrue();
    }

    @Test
    void shouldHandleShortFlags() {
        AppConfiguration config = AppConfiguration.parse(new String[]{
            "-b", "file",
            "-i", "15",
            "-v"
        });
        
        assertThat(config.getBackend()).isEqualTo("file");
        assertThat(config.getReconcileInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config.isVerbose()).isTrue();
    }

    @Test
    void shouldHandleInvalidReconcileInterval() {
        // Test negative interval
        AppConfiguration config = AppConfiguration.parse(new String[]{"--interval", "-5"});
        assertThat(config.getReconcileInterval()).isEqualTo(Duration.ofSeconds(30)); // Default

        // Test zero interval
        config = AppConfiguration.parse(new String[]{"--interval", "0"});
        assertThat(config.getReconcileInterval()).isEqualTo(Duration.ofSeconds(30)); // Default
    }

    @Test
    void shouldHandleInvalidEnvironmentIntervalGracefully() {
        environmentVariables.set("RECONCILE_INTERVAL_SECONDS", "invalid");

        AppConfiguration config = AppConfiguration.parse(new String[]{});
        assertThat(config.getReconcileInterval()).isEqualTo(Duration.ofSeconds(30)); // Default
    }

    @Test
    void shouldNormalizeBackendToLowercase() {
        AppConfiguration config = AppConfiguration.parse(new String[]{"--backend", "FILE"});
        assertThat(config.getBackend()).isEqualTo("file");

        config = AppConfiguration.parse(new String[]{"--backend", "DynamoDB"});
        assertThat(config.getBackend()).isEqualTo("dynamodb");
    }

    @Test
    void shouldConstructJmxUrlCorrectly() {
        AppConfiguration config = AppConfiguration.parse(new String[]{
            "--jmx-host", "my-host",
            "--jmx-port", "7777"
        });
        
        assertThat(config.getJmxUrl()).isEqualTo("service:jmx:rmi:///jndi/rmi://my-host:7777/jmxrmi");
    }

    @Test
    void shouldAllowSettersForTesting() {
        AppConfiguration config = new AppConfiguration();
        
        config.setBackend("dynamo");
        config.setDynamoTableName("test-table");
        config.setServiceName("test-service");
        config.setConfigPath("/test/path.yaml");
        config.setJmxHost("test-host");
        config.setJmxPort("1234");
        config.setTargetLoggerContext("TestContext");
        config.setReconcileIntervalSeconds(77L);
        config.setDryRun(true);
        config.setVerbose(true);
        
        assertThat(config.getBackend()).isEqualTo("dynamo");
        assertThat(config.getDynamoTableName()).isEqualTo("test-table");
        assertThat(config.getServiceName()).isEqualTo("test-service");
        assertThat(config.getConfigPath()).isEqualTo("/test/path.yaml");
        assertThat(config.getJmxHost()).isEqualTo("test-host");
        assertThat(config.getJmxPort()).isEqualTo("1234");
        assertThat(config.getJmxUrl()).isEqualTo("service:jmx:rmi:///jndi/rmi://test-host:1234/jmxrmi");
        assertThat(config.getTargetLoggerContext()).isEqualTo("TestContext");
        assertThat(config.getReconcileInterval()).isEqualTo(Duration.ofSeconds(77));
        assertThat(config.isDryRun()).isTrue();
        assertThat(config.isVerbose()).isTrue();
    }

    @Test
    void shouldProvideUsefulToString() {
        AppConfiguration config = AppConfiguration.parse(new String[]{
            "--backend", "file",
            "--jmx-host", "myhost"
        });
        
        String toString = config.toString();
        assertThat(toString).contains("backend='file'");
        assertThat(toString).contains("jmxHost='myhost'");
        assertThat(toString).contains("AppConfiguration{");
    }
}
