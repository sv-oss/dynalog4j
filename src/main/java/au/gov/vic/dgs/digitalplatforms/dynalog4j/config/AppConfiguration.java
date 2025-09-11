package au.gov.vic.dgs.digitalplatforms.dynalog4j.config;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.time.Duration;

/**
 * Application configuration that supports CLI arguments and environment variables.
 * CLI arguments take precedence over environment variables.
 */
@Command(name = "dynalog4j", 
         description = "Dynamic Log4j2 Level Management Tool",
         mixinStandardHelpOptions = true,
         version = "DynaLog4J 1.0.0")
public class AppConfiguration {

    // Backend Configuration
    @Option(names = {"-b", "--backend"}, 
            description = "Backend type: env, file, or dynamo (default: ${DEFAULT-VALUE})")
    private String backend = getEnvOrDefault("BACKEND", "env");

    @Option(names = {"--table-name"}, 
            description = "DynamoDB table name (default: ${DEFAULT-VALUE})")
    private String dynamoTableName = getEnvOrDefault("DYNAMO_TABLE_NAME", "log-levels");

    @Option(names = {"--service-name"}, 
            description = "Service name for DynamoDB backend (default: ${DEFAULT-VALUE})")
    private String serviceName = getEnvOrDefault("SERVICE_NAME", "default");

    @Option(names = {"--config-path"}, 
            description = "File path for file backend (default: ${DEFAULT-VALUE})")
    private String configPath = getEnvOrDefault("LOG_CONFIG_PATH", "/config/log-levels.yaml");

    // JMX Configuration
    @Option(names = {"--jmx-host"}, 
            description = "JMX host (default: ${DEFAULT-VALUE})")
    private String jmxHost = getEnvOrDefault("JMX_HOST", "localhost");

    @Option(names = {"--jmx-port"}, 
            description = "JMX port (default: ${DEFAULT-VALUE})")
    private String jmxPort = getEnvOrDefault("JMX_PORT", "9999");

    @Option(names = {"--jmx-pid"}, 
            description = "JMX process ID to attach to (alternative to host/port)")
    private String jmxPid = getEnvOrDefault("JMX_PID", null);

    @Option(names = {"--jmx-pid-filter"}, 
            description = "Process command pattern to filter discoverable PIDs (regex supported)")
    private String jmxPidFilter = getEnvOrDefault("JMX_PID_FILTER", null);

    @Option(names = {"--target-context"}, 
            description = "Target LoggerContext name (default: auto-select)")
    private String targetLoggerContext = getEnvOrDefault("TARGET_LOGGER_CONTEXT", null);

    // Application Configuration
    @Option(names = {"-i", "--interval"}, 
            description = "Reconciliation interval in seconds (default: ${DEFAULT-VALUE})")
    private Long reconcileIntervalSeconds = parseLong(getEnvOrDefault("RECONCILE_INTERVAL_SECONDS", "30"));

    @Option(names = {"--dry-run"}, 
            description = "Run in dry-run mode (no actual configuration changes)")
    private boolean dryRun = Boolean.parseBoolean(getEnvOrDefault("DRY_RUN", "false"));

    @Option(names = {"-l", "--log-level"}, 
            description = "Log level: TRACE, DEBUG, INFO, WARN, ERROR (default: ${DEFAULT-VALUE})")
    private String logLevel = getEnvOrDefault("LOG_LEVEL", "INFO");

    // Retry Configuration
    @Option(names = {"--max-attempts"}, 
            description = "Maximum number of retry attempts for main loop failures (0 = no retry, default: ${DEFAULT-VALUE})")
    private Integer maxAttempts = parseInt(getEnvOrDefault("MAX_ATTEMPTS", "0"));

    @Option(names = {"--retry-interval"}, 
            description = "Retry interval in seconds between main loop restart attempts (default: ${DEFAULT-VALUE})")
    private Long retryIntervalSeconds = parseLong(getEnvOrDefault("RETRY_INTERVAL_SECONDS", "60"));

    // Help flag is handled by picocli mixinStandardHelpOptions

    /**
     * Parse command line arguments and environment variables.
     */
    public static AppConfiguration parse(String[] args) {
        AppConfiguration config = new AppConfiguration();
        CommandLine cmd = new CommandLine(config);
        
        try {
            cmd.parseArgs(args);
            
            if (cmd.isUsageHelpRequested()) {
                cmd.usage(System.out);
                System.exit(0);
            }
            
            if (cmd.isVersionHelpRequested()) {
                cmd.printVersionHelp(System.out);
                System.exit(0);
            }
            
            return config;
        } catch (CommandLine.ParameterException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println();
            cmd.usage(System.err);
            System.exit(1);
            return null; // Never reached
        }
    }

    // Getters
    public String getBackend() {
        return backend.toLowerCase();
    }

    public String getDynamoTableName() {
        return dynamoTableName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getConfigPath() {
        return configPath;
    }

    public String getJmxHost() {
        return jmxHost;
    }

    public String getJmxPort() {
        return jmxPort;
    }

    public String getJmxPid() {
        return jmxPid;
    }

    public String getJmxPidFilter() {
        return jmxPidFilter;
    }

    public String getJmxUrl() {
        return "service:jmx:rmi:///jndi/rmi://" + jmxHost + ":" + jmxPort + "/jmxrmi";
    }

    public String getTargetLoggerContext() {
        return targetLoggerContext;
    }

    public Duration getReconcileInterval() {
        if (reconcileIntervalSeconds == null || reconcileIntervalSeconds < 1) {
            return Duration.ofSeconds(30);
        }
        return Duration.ofSeconds(reconcileIntervalSeconds);
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public String getLogLevel() {
        return logLevel != null ? logLevel.toUpperCase() : "INFO";
    }

    public int getMaxAttempts() {
        return maxAttempts != null && maxAttempts >= 0 ? maxAttempts : 0;
    }

    public Duration getRetryInterval() {
        if (retryIntervalSeconds == null || retryIntervalSeconds < 1) {
            return Duration.ofSeconds(60);
        }
        return Duration.ofSeconds(retryIntervalSeconds);
    }

    // Setters for testing
    public void setBackend(String backend) {
        this.backend = backend;
    }

    public void setDynamoTableName(String dynamoTableName) {
        this.dynamoTableName = dynamoTableName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public void setJmxHost(String jmxHost) {
        this.jmxHost = jmxHost;
    }

    public void setJmxPort(String jmxPort) {
        this.jmxPort = jmxPort;
    }

    public void setJmxPid(String jmxPid) {
        this.jmxPid = jmxPid;
    }

    public void setJmxPidFilter(String jmxPidFilter) {
        this.jmxPidFilter = jmxPidFilter;
    }

    public void setTargetLoggerContext(String targetLoggerContext) {
        this.targetLoggerContext = targetLoggerContext;
    }

    public void setReconcileIntervalSeconds(Long reconcileIntervalSeconds) {
        this.reconcileIntervalSeconds = reconcileIntervalSeconds;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setRetryIntervalSeconds(Long retryIntervalSeconds) {
        this.retryIntervalSeconds = retryIntervalSeconds;
    }

    // Utility methods
    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return value != null ? value : defaultValue;
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "AppConfiguration{" +
                "backend='" + backend + '\'' +
                ", dynamoTableName='" + dynamoTableName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", configPath='" + configPath + '\'' +
                ", jmxHost='" + jmxHost + '\'' +
                ", jmxPort='" + jmxPort + '\'' +
                ", targetLoggerContext='" + targetLoggerContext + '\'' +
                ", reconcileIntervalSeconds=" + reconcileIntervalSeconds +
                ", dryRun=" + dryRun +
                ", logLevel='" + logLevel + '\'' +
                ", maxAttempts=" + maxAttempts +
                ", retryIntervalSeconds=" + retryIntervalSeconds +
                '}';
    }
}
