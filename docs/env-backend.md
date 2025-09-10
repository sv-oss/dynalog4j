# Environment Variables Backend

The Environment Variables backend reads log level configuration from environment variables. This is the simplest backend and ideal for containerized environments where configuration is passed through environment variables.

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `LOG_LEVEL_<logger_name>` | - | Log level for specific logger (replace dots with underscores) |

### Example Configuration

```bash
# Set log levels via environment variables
export LOG_LEVEL_root=WARN
export LOG_LEVEL_com_example_MyClass=DEBUG
export LOG_LEVEL_org_springframework=INFO
```

Note: Replace dots (`.`) in logger names with underscores (`_`) for environment variable names.

## Usage

### Command Line

```bash
# Set environment variables
export BACKEND=env
export LOG_LEVEL_root=WARN
export LOG_LEVEL_com_example_MyClass=DEBUG
export LOG_LEVEL_org_springframework=INFO

# Run DynaLog4J
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/sun.reflect=ALL-UNNAMED \
     -Dnet.bytebuddy.experimental=true \
     -jar target/DynaLog4J-1.0.0.jar --backend env --verbose
```

### Docker

```bash
docker run -d \
  --network host \
  -e BACKEND=env \
  -e LOG_LEVEL_root=WARN \
  -e LOG_LEVEL_com_example=DEBUG \
  dynalog4j:latest
```

### Kubernetes

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
          value: "env"
        - name: LOG_LEVEL_root
          value: "WARN"
        - name: LOG_LEVEL_com_example_MyClass
          value: "DEBUG"
        - name: LOG_LEVEL_org_springframework
          value: "INFO"
```

## Logger Name Mapping

| Java Logger Name | Environment Variable |
|------------------|---------------------|
| `root` | `LOG_LEVEL_root` |
| `com.example.MyClass` | `LOG_LEVEL_com_example_MyClass` |
| `org.springframework.web` | `LOG_LEVEL_org_springframework_web` |
| `net.sf.hibernate` | `LOG_LEVEL_net_sf_hibernate` |

## Dynamic Updates

The Environment Variables backend reads the environment variables each time it checks for configuration changes (every 30 seconds by default). However, changing environment variables in a running process typically requires restarting the application.

For dynamic log level changes without restarts, consider using the [File Backend](file-backend.md) or [DynamoDB Backend](dynamodb-backend.md).

## Advantages

- **Simple**: No external dependencies or setup required
- **Container-friendly**: Works well with Docker and Kubernetes
- **Immutable**: Configuration is set at container startup

## Disadvantages

- **Static**: Requires restart to change log levels
- **Limited**: Cannot expire log levels automatically
- **Verbose**: Environment variable names can become long for deeply nested loggers
