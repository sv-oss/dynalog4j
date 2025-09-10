# DynaLog4J

Dynamic Log4j2 Level Management Tool

## Overview

DynaLog4J is a Java application designed to dynamically manage log levels of a co-resident Java application using Log4j2, without requiring application restarts. The application runs a reconciliation loop that reads the live Log4j2 configuration via JMX, merges desired overrides from a pluggable backend, and writes the updated configuration back.

## Features

- **Zero-downtime log level changes**: Update log levels without restarting your application
- **JMX-based**: Uses standard JMX APIs to communicate with Log4j2
- **Auto-discovery**: Automatically discovers Log4j2 LoggerContexts in the target JVM
- **Pluggable backends**: Support for environment variables, files, and DynamoDB
- **CLI-friendly**: Comprehensive command-line interface with help and configuration options
- **Container-ready**: Packaged as a slim Docker container for easy deployment
- **Kubernetes-friendly**: Designed to run as a sidecar in Kubernetes pods
- **Dry-run support**: Test configuration changes without applying them
- **Testable**: Clean configuration abstraction makes testing easy

## Command Line Interface

### Help and Version

```bash
java -jar DynaLog4J-1.0.0.jar --help
java -jar DynaLog4J-1.0.0.jar --version
```

### Configuration Options

All options can be provided via CLI arguments or environment variables. CLI arguments take precedence.

| CLI Argument | Short | Environment Variable | Default | Description |
|--------------|-------|---------------------|---------|-------------|
| `--backend` | `-b` | `BACKEND` | `env` | Backend type: env, file, or dynamo |
| `--table-name` | | `DYNAMO_TABLE_NAME` | `log-levels` | DynamoDB table name |
| `--service-name` | | `SERVICE_NAME` | `default` | Service name for DynamoDB backend |
| `--config-path` | | `LOG_CONFIG_PATH` | `/config/log-levels.yaml` | File path for file backend |
| `--jmx-host` | | `JMX_HOST` | `localhost` | JMX host |
| `--jmx-port` | | `JMX_PORT` | `9999` | JMX port |
| `--target-context` | | `TARGET_LOGGER_CONTEXT` | auto-select | Target LoggerContext name |
| `--interval` | `-i` | `RECONCILE_INTERVAL_SECONDS` | `30` | Reconciliation interval in seconds |
| `--dry-run` | | `DRY_RUN` | `false` | Run in dry-run mode (no actual changes) |
| `--verbose` | `-v` | `VERBOSE` | `false` | Enable verbose logging |

### Examples

#### Basic usage with environment backend
```bash
java -jar DynaLog4J-1.0.0.jar --backend env --interval 60
```

#### File backend with custom path
```bash
java -jar DynaLog4J-1.0.0.jar -b file --config-path /etc/log-config.yaml
```

#### DynamoDB backend with dry-run
```bash
java -jar DynaLog4J-1.0.0.jar \
  --backend dynamo \
  --table-name my-log-table \
  --service-name my-service \
  --dry-run
```

#### Custom JMX configuration
```bash
java -jar DynaLog4J-1.0.0.jar \
  --jmx-host remote-server \
  --jmx-port 8888 \
  --target-context MyAppContext \
  --verbose
```

## Core Architecture

### Reconciliation Loop

1. **Discover target JVM**: Connect to its JMX endpoint
2. **Detect contexts**: Auto-detect or explicitly specify Log4j2 LoggerContext
3. **Read current config**: Fetch full XML configuration via JMX
4. **Reconcile**: Merge desired log level overrides from backend
5. **Write updated config**: Push updated XML back via JMX (unless in dry-run mode)
6. **Repeat**: Continuously sync at configured interval

### Backend Plugin Model

```java
public interface Backend {
    Map<String, String> fetchDesiredLevels() throws Exception;
}
```

#### Available Backends

- **EnvBackend**: Reads from environment variables (e.g., `LOG_LEVEL_com.myco.Class=DEBUG`)
- **FileBackend**: Reads YAML/JSON from mounted ConfigMap
- **DynamoDBBackend**: Queries DynamoDB table keyed by service/app

## Prerequisites

- **Java 21**: Required for building and running the application
- **Maven 3.6+**: For building the project
- **Docker** (optional): For containerized deployment

## Quick Start

### 1. Build the Application

**Requirements**: Java 21 and Maven 3.6+

```bash
mvn clean package
```

This creates a shaded JAR: `target/DynaLog4J-1.0.0.jar`

### 2. Build Docker Image

```bash
docker build -t dynalog4j:latest .
```

### 3. Configure Your Main Application

Enable JMX in your main Java application:

```bash
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9999 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -Dcom.sun.management.jmxremote.local.only=false \
     -jar your-app.jar
```

**Note**: Your target application must also be running on Java 21 for optimal compatibility.

### 4. Run DynaLog4J

#### Using Environment Variables Backend

```bash
# Via CLI arguments (requires Java 21)
java -jar DynaLog4J-1.0.0.jar --backend env

# Or via Docker with environment variables
docker run -d \
  --network host \
  -e BACKEND=env \
  -e LOG_LEVEL_root=WARN \
  -e LOG_LEVEL_com.example=DEBUG \
  -e JMX_PORT=9999 \
  dynalog4j:latest
```

#### Using File Backend

```bash
# Via CLI arguments
java -jar DynaLog4J-1.0.0.jar \
  --backend file \
  --config-path /config/log-levels.yaml

# Or via Docker
docker run -d \
  --network host \
  -e BACKEND=file \
  -e LOG_CONFIG_PATH=/config/log-levels.yaml \
  -v /path/to/config:/config:ro \
  dynalog4j:latest
```

#### Using DynamoDB Backend

```bash
# Via CLI arguments
java -jar DynaLog4J-1.0.0.jar \
  --backend dynamo \
  --table-name my-log-levels \
  --service-name my-app

# Or via Docker
docker run -d \
  --network host \
  -e BACKEND=dynamodb \
  -e DYNAMO_TABLE_NAME=log-levels \
  -e SERVICE_NAME=my-app \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=... \
  -e AWS_SECRET_ACCESS_KEY=... \
  dynalog4j:latest
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKEND` | `env` | Backend type: `env`, `file`, or `dynamodb` |
| `RECONCILE_INTERVAL_SECONDS` | `30` | How often to check for config changes |
| `JMX_HOST` | `localhost` | JMX endpoint hostname |
| `JMX_PORT` | `9999` | JMX endpoint port |
| `TARGET_LOGGER_CONTEXT` | (auto-detect) | Specific LoggerContext name to target |

#### Environment Variables Backend

Set log levels using `LOG_LEVEL_<logger.name>=<level>` format:

```bash
LOG_LEVEL_root=WARN
LOG_LEVEL_com.mycompany.myapp=DEBUG
LOG_LEVEL_org.springframework=INFO
```

#### File Backend

| Variable | Default | Description |
|----------|---------|-------------|
| `LOG_CONFIG_PATH` | `/config/log-levels.yaml` | Path to YAML/JSON config file |

Example YAML file:
```yaml
loggers:
  root: WARN
  com.mycompany.myapp: DEBUG
  com.mycompany.myapp.service: INFO
  org.springframework: INFO
```

#### DynamoDB Backend

| Variable | Default | Description |
|----------|---------|-------------|
| `DYNAMO_TABLE_NAME` | `log-levels` | DynamoDB table name |
| `SERVICE_NAME` | `default` | Service identifier (table key) |
| `AWS_REGION` | `us-east-1` | AWS region |

Expected DynamoDB item structure:
```json
{
  "service": "my-app",
  "loggers": {
    "root": "WARN",
    "com.mycompany.myapp": "DEBUG"
  }
}
```

## Kubernetes Deployment

See `examples/kubernetes-deployment.yaml` for a complete example. Key points:

1. **Same pod**: Sidecar and main app must be in the same pod to share localhost
2. **JMX configuration**: Enable JMX in your main application
3. **ConfigMap**: Use ConfigMap to provide log level configuration
4. **Resource limits**: Sidecar is lightweight, typically needs 128-256MB RAM

```yaml
containers:
- name: my-app
  image: my-app:latest
  env:
  - name: JAVA_OPTS
    value: "-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 ..."

- name: dynalog4j-sidecar
  image: dynalog4j:latest
  env:
  - name: BACKEND
    value: "file"
  - name: LOG_CONFIG_PATH
    value: "/config/log-levels.yaml"
```

## Development

### Building

```bash
mvn clean compile
```

### Testing

```bash
mvn test
```

### Running Locally

```bash
# Start your main application with JMX enabled
java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 ... -jar your-app.jar

# In another terminal, run the sidecar
mvn exec:java -Dexec.mainClass="org.acme.sidecar.LogSidecar"
```

## Supported Log Levels

- `TRACE`
- `DEBUG` 
- `INFO`
- `WARN`
- `ERROR`
- `FATAL`
- `OFF`

## Security Considerations

- **JMX Security**: This example disables JMX authentication for simplicity. In production, configure proper JMX security
- **Network isolation**: Sidecar only needs localhost access to the main application
- **Least privilege**: Container runs as non-root user
- **Backend access**: Secure your configuration backend (file permissions, AWS IAM, etc.)

## Troubleshooting

### Common Issues

1. **"No LoggerContexts found"**: Ensure your main app uses Log4j2 and has JMX enabled
2. **"Connection refused"**: Check JMX port and ensure both containers can reach localhost
3. **"Configuration unchanged"**: Verify your backend is returning the expected log levels

**Docker Compose specific issues:**

4. **"Connection refused to host: xxx.xxx.xxx.xxx"**: When running main app in Docker and sidecar on host:
   ```yaml
   # In your docker-compose.yml, add these JVM args to your main app:
   environment:
     - JAVA_OPTS=-Dcom.sun.management.jmxremote.local.only=false -Djava.rmi.server.hostname=localhost -Djava.net.preferIPv4Stack=true -Dcom.sun.management.jmxremote.rmi.port=9999 -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false
   ports:
     - "9999:9999"
   ```

5. **IPv6 vs IPv4**: If JMX shows `:::9999` in netstat, add `-Djava.net.preferIPv4Stack=true` to force IPv4

### Logging

The sidecar logs its actions at INFO level. To see more details:

```bash
# Add debug logging
-e LOG_LEVEL_org.acme.sidecar=DEBUG
```

### Health Check

The Docker image includes a health check that verifies the Java process is running:

```bash
docker exec <container-id> pgrep -f "java.*dynalog4j"
```

## License

This project is licensed under the Apache License 2.0.