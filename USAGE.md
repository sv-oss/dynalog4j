# DynaLog4J - Usage Guide

DynaLog4J is a dynamic log level management sidecar for Java applications using Log4j2. It allows you to change log levels at runtime without restarting your application.

## Documentation

### Getting Started
- **[Getting Started Guide](docs/getting-started.md)** - Quick start and basic configuration

### Backend Configuration
- **[Environment Variables Backend](docs/env-backend.md)** - Simple environment variable configuration
- **[File Backend](docs/file-backend.md)** - YAML/JSON file-based configuration with dynamic updates
- **[DynamoDB Backend](docs/dynamodb-backend.md)** - AWS DynamoDB with TTL support and centralized management


## Quick Examples

### Environment Variables Backend
```bash
export BACKEND=env
export LOG_LEVEL_root=WARN
export LOG_LEVEL_com_example_MyClass=DEBUG
java -jar target/DynaLog4J-1.0.0.jar
```

### File Backend
```bash
export BACKEND=file
export LOG_CONFIG_PATH=/config/log-levels.yaml
java -jar target/DynaLog4J-1.0.0.jar
```

### DynamoDB Backend
```bash
export BACKEND=dynamodb
export DYNAMO_TABLE_NAME=log-levels
export SERVICE_NAME=my-app
java -jar target/DynaLog4J-1.0.0.jar
```

## Log Level Configuration

DynaLog4J supports configurable log levels to control verbosity:

### Quiet Mode (Recommended for Production)
```bash
# Only show warnings, errors, and active configuration changes
java -jar target/DynaLog4J-1.0.0.jar --log-level WARN --backend env
```

### Normal Mode (Default)
```bash
# Show active actions and changes (default level)
java -jar target/DynaLog4J-1.0.0.jar --log-level INFO --backend env
```

### Debug Mode
```bash
# Show detailed flow information for troubleshooting
java -jar target/DynaLog4J-1.0.0.jar --log-level DEBUG --backend env
```

### Trace Mode
```bash
# Show very detailed debugging information
java -jar target/DynaLog4J-1.0.0.jar --log-level TRACE --backend env
```

**Log Level Behavior:**
- **WARN/ERROR**: Only errors, warnings, and configuration changes
- **INFO**: Active actions (startup, configuration updates, connections)
- **DEBUG**: Detailed flow with periodic heartbeat
- **TRACE**: Very detailed debugging including reconciliation cycles

## Retry Configuration

DynaLog4J supports automatic retry on main loop failures for improved resilience:

### With Retry (Recommended for Production)
```bash
# Retry up to 5 times with 2-minute intervals
export MAX_ATTEMPTS=5
export RETRY_INTERVAL_SECONDS=120
java -jar target/DynaLog4J-1.0.0.jar --backend env

# Or via CLI arguments
java -jar target/DynaLog4J-1.0.0.jar \
  --backend dynamo \
  --table-name log-levels \
  --service-name my-app \
  --max-attempts 3 \
  --retry-interval 60
```

### Without Retry (Default)
```bash
# Fail immediately on errors (default behavior)
java -jar target/DynaLog4J-1.0.0.jar --backend env
```

**Use Cases for Retry:**
- Production environments where resilience is critical
- Network-dependent backends (DynamoDB, remote files)
- Deployments where target applications may restart
- Container environments with potential temporary failures