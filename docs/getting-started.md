# DynaLog4J - Getting Started

## Quick Start Guide

**Prerequisites**: Java 21 and Maven 3.6+

### 1. Build the Project

```bash
mvn clean package
```

This creates a shaded JAR: `target/DynaLog4J-1.0.0.jar`

### 2. Set Up Your Main Application

Enable JMX in your target Java application (must be running on Java 21):

```bash
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9999 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -Dcom.sun.management.jmxremote.local.only=false \
     -jar your-app.jar
```

### 3. Choose Your Backend

DynaLog4J supports three backends for log level configuration:

- **[Environment Variables Backend](env-backend.md)** - Simple environment variable configuration
- **[File Backend](file-backend.md)** - YAML/JSON file-based configuration
- **[DynamoDB Backend](dynamodb-backend.md)** - AWS DynamoDB with TTL support

### 4. Run DynaLog4J

#### CLI Arguments (Recommended)

```bash
# Environment variables backend
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/sun.reflect=ALL-UNNAMED \
     -Dnet.bytebuddy.experimental=true \
     -jar target/DynaLog4J-1.0.0.jar --backend env --verbose

# File backend
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/sun.reflect=ALL-UNNAMED \
     -Dnet.bytebuddy.experimental=true \
     -jar target/DynaLog4J-1.0.0.jar \
     --backend file \
     --config-path /config/log-levels.yaml \
     --interval 60

# DynamoDB backend
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/sun.reflect=ALL-UNNAMED \
     -Dnet.bytebuddy.experimental=true \
     -jar target/DynaLog4J-1.0.0.jar \
     --backend dynamo \
     --table-name my-log-levels \
     --service-name my-app \
     --dry-run
```

## Configuration Options

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `BACKEND` | `env` | Backend type: `env`, `file`, or `dynamodb` |
| `RECONCILE_INTERVAL_SECONDS` | `30` | How often to check for changes |
| `JMX_HOST` | `localhost` | JMX endpoint hostname |
| `JMX_PORT` | `9999` | JMX endpoint port |
| `TARGET_LOGGER_CONTEXT` | (auto-detect) | Specific LoggerContext name or regex pattern |

## LoggerContext Selection

DynaLog4J automatically discovers Log4j2 LoggerContexts but allows you to specify a target:

### Pattern Matching
- **Exact match**: `TARGET_LOGGER_CONTEXT="MyAppContext"`
- **Regex patterns**: `TARGET_LOGGER_CONTEXT="^(?!Tomcat$).*"` (exclude Tomcat)
- **Hash matching**: `TARGET_LOGGER_CONTEXT="[a-f0-9]{8}"` (8-char hex hashes)

### Examples
```bash
# Exclude system contexts
export TARGET_LOGGER_CONTEXT="^(?!Tomcat$).*"

# Match application contexts only
export TARGET_LOGGER_CONTEXT=".*App.*"
```

## Troubleshooting

### Common Issues

1. **"No LoggerContexts found"**
   - Ensure your main app uses Log4j2 and has JMX enabled
   - Check that both apps can reach localhost

2. **"Connection refused"**
   - Verify JMX port and that both containers share localhost
   - Check firewall settings

3. **"Configuration unchanged"**
   - Verify your backend is returning the expected log levels
   - Check the sidecar logs for backend errors

4. **"No LoggerContext found matching pattern"**
   - Check available contexts with verbose logging: `--verbose`
   - Verify your `TARGET_LOGGER_CONTEXT` pattern syntax
   - Use exact context name if regex pattern fails

### Enable Debug Logging

```bash
export LOG_LEVEL_org.acme.sidecar=DEBUG
java -jar target/dynalog4j-1.0.0.jar
```

### Test Connectivity

Check if your main app's JMX is accessible:
```bash
jconsole localhost:9999
```

## Monitoring

The sidecar logs its actions at INFO level:
- Connection status
- Configuration changes applied
- Reconciliation cycle status
- Backend errors

Example log output:
```
22:13:05.089 INFO org.acme.sidecar.backend.BackendFactory - Using environment variables backend
22:13:05.097 INFO org.acme.sidecar.LogSidecar - Initializing sidecar with reconcile interval: PT30S
22:13:05.097 INFO org.acme.sidecar.jmx.JMXManager - Connecting to JMX endpoint: service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi
```
