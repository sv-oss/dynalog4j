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