# Example environment variables for log level configuration
# These can be set in your deployment environment

# Backend configuration
BACKEND=env
RECONCILE_INTERVAL_SECONDS=30

# JMX connection settings
JMX_HOST=localhost
JMX_PORT=9999

# Target LoggerContext (optional - will auto-detect if not specified)
# TARGET_LOGGER_CONTEXT=MyApp

# Log level overrides (for env backend)
LOG_LEVEL_root=WARN
LOG_LEVEL_com.mycompany.myapp=DEBUG
LOG_LEVEL_com.mycompany.myapp.service=INFO
LOG_LEVEL_com.mycompany.myapp.dao=WARN
LOG_LEVEL_org.springframework=INFO
LOG_LEVEL_org.hibernate=WARN
LOG_LEVEL_org.apache.http=ERROR
LOG_LEVEL_com.mycompany.myapp.service.PaymentService=DEBUG
