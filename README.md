# DynaLog4J

Dynamic Log4j2 Level Management Tool

## Overview

DynaLog4J is a Java application designed to dynamically manage log levels of a co-resident Java application using Log4j2, without requiring application restarts. The application runs a reconciliation loop that reads the live Log4j2 configuration via JMX, merges desired overrides from a pluggable backend, and writes the updated configuration back.

## Features

- **Zero-downtime log level changes**: Update log levels without restarting your application
- **JMX-based**: Uses standard JMX APIs to communicate with Log4j2
- **Auto-discovery**: Automatically discovers Log4j2 LoggerContexts in the target JVM
- **Process attachment**: Attach to Java processes by PID with automatic JMX agent initialization
- **Smart process discovery**: Auto-detect target applications or filter by command patterns
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
| `--jmx-pid` | | `JMX_PID` | (auto-discover) | Process ID to attach to (alternative to host/port) |
| `--jmx-pid-filter` | | `JMX_PID_FILTER` | (none) | Process command pattern to filter discoverable PIDs |
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

#### Process ID (PID) attachment
```bash
# Attach to a specific Java process by PID
java -jar DynaLog4J-1.0.0.jar --jmx-pid 12345

# Auto-discover and attach to a Java process (single process)
java -jar DynaLog4J-1.0.0.jar

# Auto-discover with process filter (regex or substring matching)
java -jar DynaLog4J-1.0.0.jar --jmx-pid-filter "my-application"
java -jar DynaLog4J-1.0.0.jar --jmx-pid-filter ".*spring-boot.*"
```

## Core Architecture

### JMX Connection Methods

DynaLog4J supports multiple methods to connect to your target Java application:

1. **Process ID (PID) Attachment** (Recommended): Automatically discovers and attaches to Java processes
   - Auto-discovery: Scans for attachable Java processes if no PID specified
   - Direct PID: Connect to a specific process using `--jmx-pid`
   - Filtered discovery: Use `--jmx-pid-filter` to match specific applications
   - Automatic JMX agent initialization if not already enabled

2. **Traditional JMX URL**: Connect via host/port (requires pre-configured JMX endpoint)

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

### 3. Configure Your Main Application (Optional for PID mode)

For PID attachment mode, JMX configuration is handled automatically. For traditional JMX connections, enable JMX in your main Java application:

```bash
java -Dlog4j2.disableJmx=false -jar your-app.jar
```

**Note**: When using PID attachment (`--jmx-pid` or auto-discovery), DynaLog4J can automatically start the JMX management agent if it's not already running, making explicit JMX configuration optional.

### 4. Run DynaLog4J

#### Using Environment Variables Backend

```bash
# Via CLI arguments (requires Java 21)
java -jar DynaLog4J-1.0.0.jar --backend env

# Using PID attachment (auto-discover Java processes)
java -jar DynaLog4J-1.0.0.jar --backend env

# Using specific PID
java -jar DynaLog4J-1.0.0.jar --backend env --jmx-pid 12345

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

### Connection Methods

DynaLog4J offers flexible connection options:

1. **Auto-discovery**: Automatically finds and attaches to Java processes
2. **PID-based**: Attach to specific process IDs  
3. **Traditional JMX**: Connect via host/port endpoints

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKEND` | `env` | Backend type: `env`, `file`, or `dynamodb` |
| `RECONCILE_INTERVAL_SECONDS` | `30` | How often to check for config changes |
| `JMX_HOST` | `localhost` | JMX endpoint hostname (for traditional JMX) |
| `JMX_PORT` | `9999` | JMX endpoint port (for traditional JMX) |
| `JMX_PID` | (auto-discover) | Process ID to attach to |
| `JMX_PID_FILTER` | (none) | Filter pattern for process auto-discovery |
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

## PID Attachment Features

DynaLog4J includes powerful process attachment capabilities that eliminate the need for pre-configured JMX endpoints:

### Auto-Discovery

When no specific PID or JMX URL is provided, DynaLog4J automatically discovers attachable Java processes:

```bash
# Automatically finds and attaches to the single Java process
java -jar DynaLog4J-1.0.0.jar --backend env
```

If multiple Java processes are found, DynaLog4J lists them for manual selection:
```
Multiple attachable Java processes found. Please specify --jmx-pid:
  - PID: 12345
  - PID: 67890
```

### Filtered Discovery

Use `--jmx-pid-filter` to automatically select processes matching a pattern:

```bash
# Match by application name (substring matching)
java -jar DynaLog4J-1.0.0.jar --jmx-pid-filter "spring-boot"

# Match by regex pattern
java -jar DynaLog4J-1.0.0.jar --jmx-pid-filter ".*myapp.*"

# Match by JAR name
java -jar DynaLog4J-1.0.0.jar --jmx-pid-filter "my-application.jar"
```

### Direct PID Attachment

Connect to a specific process by PID:

```bash
java -jar DynaLog4J-1.0.0.jar --jmx-pid 12345
```

### Automatic JMX Agent Initialization

When attaching via PID, DynaLog4J automatically:
- Detects if the JMX management agent is running
- Starts the local management agent if needed
- Obtains the JMX connector address
- Establishes the connection

This means your target application doesn't need any special JMX configuration.

## Kubernetes Deployment

See `examples/kubernetes-deployment.yaml` for a complete example. Key points:

1. **Same pod**: Sidecar and main app must be in the same pod to share localhost
2. **JMX configuration**: When using PID attachment, JMX configuration is optional as DynaLog4J can auto-initialize the JMX agent
3. **ConfigMap**: Use ConfigMap to provide log level configuration
4. **Resource limits**: Sidecar is lightweight, typically needs 128-256MB RAM

```yaml
containers:
- name: my-app
  image: my-app:latest
  # JMX configuration optional when using PID attachment
  
- name: dynalog4j-sidecar
  image: dynalog4j:latest
  env:
  - name: BACKEND
    value: "file"
  - name: LOG_CONFIG_PATH
    value: "/config/log-levels.yaml"
  # PID auto-discovery will find the main app automatically
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

## Supported Log Levels

- `TRACE`
- `DEBUG` 
- `INFO`
- `WARN`
- `ERROR`
- `FATAL`
- `OFF`

## License

This project is licensed under the MIT License