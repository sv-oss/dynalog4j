#!/bin/bash

# Build script for dynalog4j sidecar

set -e

echo "Building dynalog4j sidecar..."

# Clean and compile
echo "Cleaning and compiling..."
mvn clean compile

# Run tests
echo "Running tests..."
mvn test

# Package shaded JAR
echo "Creating shaded JAR..."
mvn package

# Build Docker image
echo "Building Docker image..."
docker build -t dynalog4j:latest .

echo "Build complete!"
echo "Shaded JAR: target/dynalog4j-1.0.0-shaded.jar"
echo "Docker image: dynalog4j:latest"

# Optional: Run basic validation
if [ "$1" = "--validate" ]; then
    echo "Running validation..."
    
    # Check JAR can be executed
    echo "Testing JAR execution..."
    timeout 5s java -jar target/dynalog4j-1.0.0-shaded.jar || echo "JAR execution test completed (expected to timeout)"
    
    # Check Docker image
    echo "Testing Docker image..."
    docker run --rm dynalog4j:latest --help || echo "Docker image test completed"
fi

echo "All done!"
