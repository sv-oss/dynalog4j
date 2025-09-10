FROM eclipse-temurin:21-jdk

LABEL maintainer="Your Team <team@yourcompany.com>"
LABEL description="Dynamic Log4j2 Level Management Tool"

# Create directories
RUN mkdir -p /app /config /tmp

# Copy the shaded JAR
COPY target/DynaLog4J-*.jar /app/dynalog4j.jar

# Set working directory
WORKDIR /app

# Environment variables with defaults
ENV BACKEND=env
ENV RECONCILE_INTERVAL_SECONDS=30
ENV JMX_HOST=localhost
ENV JMX_PORT=9999
ENV LOG_CONFIG_PATH=/config/log-levels.yaml

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD pgrep -f "java.*dynalog4j" > /dev/null || exit 1

# Run the sidecar
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=80.0", "-jar", "/app/dynalog4j.jar"]
