# File Backend

The File backend reads log level configuration from YAML or JSON files. This backend supports dynamic updates by monitoring the file for changes, making it ideal for development environments or when you need to change log levels without restarting the application.

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `LOG_CONFIG_PATH` | `/config/log-levels.yaml` | Path to configuration file |

### File Formats

#### YAML Format

```yaml
loggers:
  root: WARN
  com.example.MyClass: DEBUG
  org.springframework: INFO
  org.springframework.web: DEBUG
  net.sf.hibernate: WARN
```

#### JSON Format

```json
{
  "loggers": {
    "root": "WARN",
    "com.example.MyClass": "DEBUG",
    "org.springframework": "INFO",
    "org.springframework.web": "DEBUG",
    "net.sf.hibernate": "WARN"
  }
}
```

## Usage

### Command Line

```bash
# Create configuration file
cat > /config/log-levels.yaml << EOF
loggers:
  root: WARN
  com.example.MyClass: DEBUG
  org.springframework: INFO
EOF

# Set environment variables
export BACKEND=file
export LOG_CONFIG_PATH=/config/log-levels.yaml

# Run DynaLog4J
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/sun.reflect=ALL-UNNAMED \
     -Dnet.bytebuddy.experimental=true \
     -jar target/DynaLog4J-1.0.0.jar \
     --backend file \
     --config-path /config/log-levels.yaml \
     --interval 30
```

### Docker

```bash
# Create config directory
mkdir -p /path/to/config
cat > /path/to/config/log-levels.yaml << EOF
loggers:
  root: WARN
  com.example: DEBUG
EOF

# Run with file backend
docker run -d \
  --network host \
  -e BACKEND=file \
  -e LOG_CONFIG_PATH=/config/log-levels.yaml \
  -v /path/to/config:/config:ro \
  dynalog4j:latest
```

### Kubernetes

```yaml
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: log-levels-config
data:
  log-levels.yaml: |
    loggers:
      root: WARN
      com.example.MyClass: DEBUG
      org.springframework: INFO
---
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

## Dynamic Updates

The File backend monitors the configuration file for changes and automatically reloads when the file is modified. This allows you to change log levels without restarting the application.

### Example: Changing Log Levels

```bash
# Initial configuration
echo "loggers:
  root: INFO
  com.example: INFO" > /config/log-levels.yaml

# Wait a moment, then change to debug mode
echo "loggers:
  root: INFO
  com.example: DEBUG" > /config/log-levels.yaml

# The changes will be applied within the next reconciliation cycle (30 seconds by default)
```

## Supported Log Levels

- `TRACE`
- `DEBUG`
- `INFO`
- `WARN`
- `ERROR`
- `FATAL`
- `OFF`

## File Validation

The backend validates the configuration file format and will log warnings for:
- Invalid YAML/JSON syntax
- Invalid log levels
- Missing `loggers` section

Invalid configurations are ignored, and the backend continues with the last valid configuration.

## Advantages

- **Dynamic**: Changes apply without restart
- **Human-readable**: YAML/JSON formats are easy to edit
- **Version control**: Configuration files can be versioned
- **Flexible**: Supports complex logger hierarchies

## Disadvantages

- **File management**: Requires file system access
- **No TTL**: Cannot automatically expire log levels
- **Manual updates**: Requires manual file editing for changes

## Tips

1. **Use YAML**: YAML format is more human-readable than JSON
2. **Validate syntax**: Use a YAML/JSON validator before updating files
3. **Monitor logs**: Watch DynaLog4J logs for file parsing errors
4. **Use ConfigMaps**: In Kubernetes, use ConfigMaps for easy updates
