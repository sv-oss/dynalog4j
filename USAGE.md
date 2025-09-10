# DynaLog4J - Usage Examples

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

### 3. Run DynaLog4J

#### Option A: CLI Arguments (Recommended)

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

#### Option B: Environment Variables Backend (Legacy)

```bash
# Set log levels via environment variables
export BACKEND=env
export LOG_LEVEL_root=WARN
export LOG_LEVEL_com.example.MyClass=DEBUG
export LOG_LEVEL_org.springframework=INFO

# Run DynaLog4J
java -jar target/DynaLog4J-1.0.0.jar
```

#### Option C: File Backend

Create a configuration file (`/config/log-levels.yaml`):
```yaml
loggers:
  root: WARN
  com.example.MyClass: DEBUG
  org.springframework: INFO
```

Run the sidecar:
```bash
export BACKEND=file
export LOG_CONFIG_PATH=/config/log-levels.yaml
java -jar target/dynalog4j-1.0.0.jar
```

#### Option C: DynamoDB Backend

Set up DynamoDB table and run:
```bash
export BACKEND=dynamodb
export DYNAMO_TABLE_NAME=log-levels
export SERVICE_NAME=my-app
export AWS_REGION=us-east-1
java -jar target/dynalog4j-1.0.0.jar
```

## Configuration Options

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `BACKEND` | `env` | Backend type: `env`, `file`, or `dynamodb` |
| `RECONCILE_INTERVAL_SECONDS` | `30` | How often to check for changes |
| `JMX_HOST` | `localhost` | JMX endpoint hostname |
| `JMX_PORT` | `9999` | JMX endpoint port |
| `TARGET_LOGGER_CONTEXT` | (auto-detect) | Specific LoggerContext name |

### File Backend Options
| Variable | Default | Description |
|----------|---------|-------------|
| `LOG_CONFIG_PATH` | `/config/log-levels.yaml` | Path to config file |

### DynamoDB Backend Options
| Variable | Default | Description |
|----------|---------|-------------|
| `DYNAMO_TABLE_NAME` | `log-levels` | DynamoDB table name |
| `SERVICE_NAME` | `default` | Service identifier |
| `AWS_REGION` | `us-east-1` | AWS region |

## Docker Usage

### Build Docker Image

```bash
docker build -t dynalog4j:latest .
```

### Run with Environment Variables

```bash
docker run -d \
  --network host \
  -e BACKEND=env \
  -e LOG_LEVEL_root=WARN \
  -e LOG_LEVEL_com.example=DEBUG \
  dynalog4j:latest
```

### Run with File Configuration

```bash
docker run -d \
  --network host \
  -e BACKEND=file \
  -e LOG_CONFIG_PATH=/config/log-levels.yaml \
  -v /path/to/config:/config:ro \
  dynalog4j:latest
```

## Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app-with-sidecar
spec:
  template:
    spec:
      containers:
      # Main application
      - name: my-app
        image: my-app:latest
        env:
        - name: JAVA_OPTS
          value: "-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 ..."
        
      # dynalog4j sidecar
      - name: dynalog4j-sidecar
        image: dynalog4j:latest
        env:
        - name: BACKEND
          value: "file"
        - name: LOG_CONFIG_PATH
          value: "/config/log-levels.yaml"
        volumeMounts:
        - name: log-config
          mountPath: /config
          readOnly: true
      
      volumes:
      - name: log-config
        configMap:
          name: log-levels-config
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

## Sample Applications

See the `examples/` directory for:
- Sample configuration files
- Kubernetes deployment manifests
- Docker Compose setups
- Environment variable examples

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
