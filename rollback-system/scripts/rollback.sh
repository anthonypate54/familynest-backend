#!/bin/bash
# rollback.sh - Rolls back Spring Boot application to a previous version
# Usage: ./rollback.sh [version_tag]

# Set variables
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="/Users/Anthony/projects/familynest-project/familynest-backend"
VERSIONS_DIR="$BASE_DIR/versions"
CONFIGS_DIR="$BASE_DIR/configs"
LOGS_DIR="$BASE_DIR/logs"

# Create timestamp
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="$LOGS_DIR/rollback_$TIMESTAMP.log"

# Start logging
echo "=== Rollback started at $(date) ===" | tee -a "$LOG_FILE"

# Check if version tag is provided
if [ -z "$1" ]; then
    echo "No version specified, rolling back to latest known good version" | tee -a "$LOG_FILE"
    VERSION_JAR="$VERSIONS_DIR/latest.jar"
    VERSION_CONFIG="$CONFIGS_DIR/latest"
else
    VERSION_TAG="$1"
    echo "Rolling back to version: $VERSION_TAG" | tee -a "$LOG_FILE"
    
    # Find the JAR file for this version
    VERSION_JAR=$(find "$VERSIONS_DIR" -name "*-${VERSION_TAG}.jar" | head -1)
    VERSION_CONFIG="$CONFIGS_DIR/$VERSION_TAG"
    
    if [ ! -f "$VERSION_JAR" ]; then
        echo "ERROR: No JAR file found for version $VERSION_TAG" | tee -a "$LOG_FILE"
        echo "Available versions:"
        find "$VERSIONS_DIR" -name "*.jar" -printf "%f\n" | sed 's/.*-\(.*\)\.jar/\1/' | sort
        exit 1
    fi
fi

echo "Using JAR: $VERSION_JAR" | tee -a "$LOG_FILE"
echo "Using config directory: $VERSION_CONFIG" | tee -a "$LOG_FILE"

# Check if the application is running
PID=$(pgrep -f "java.*familynest-backend.*\.jar")
if [ ! -z "$PID" ]; then
    echo "Stopping current application (PID: $PID)" | tee -a "$LOG_FILE"
    kill "$PID"
    sleep 5
    
    # Check if it's still running
    if ps -p "$PID" > /dev/null; then
        echo "Application still running, forcing shutdown" | tee -a "$LOG_FILE"
        kill -9 "$PID"
        sleep 2
    fi
else
    echo "No running application found" | tee -a "$LOG_FILE"
fi

# Restore configuration files if they exist
if [ -d "$VERSION_CONFIG" ]; then
    echo "Restoring configuration files from $VERSION_CONFIG" | tee -a "$LOG_FILE"
    cp "$VERSION_CONFIG/application*.properties" "$APP_DIR/src/main/resources/" 2>/dev/null || echo "No config files to restore" | tee -a "$LOG_FILE"
else
    echo "WARNING: No configuration directory found for this version" | tee -a "$LOG_FILE"
fi

# Create a deployment directory if it doesn't exist
DEPLOY_DIR="$APP_DIR/deploy"
mkdir -p "$DEPLOY_DIR"

# Copy the JAR to the deployment directory
ROLLBACK_JAR="$DEPLOY_DIR/familynest-backend-rollback.jar"
echo "Copying rollback JAR to $ROLLBACK_JAR" | tee -a "$LOG_FILE"
cp "$VERSION_JAR" "$ROLLBACK_JAR"

# Start the application
echo "Starting application from rollback JAR" | tee -a "$LOG_FILE"
cd "$APP_DIR"

# For local testing, use a simple java command
# In production, you would use your normal startup script
java -jar "$ROLLBACK_JAR" --spring.profiles.active=local > "$LOGS_DIR/app_$TIMESTAMP.log" 2>&1 &
NEW_PID=$!

echo "Application started with PID: $NEW_PID" | tee -a "$LOG_FILE"

# Wait for application to start
echo "Waiting for application to start..." | tee -a "$LOG_FILE"
sleep 10

# Check if the application is running
if ps -p "$NEW_PID" > /dev/null; then
    echo "Application successfully started" | tee -a "$LOG_FILE"
    
    # Run health check
    if [ -f "$SCRIPT_DIR/health-check.sh" ]; then
        echo "Running health check" | tee -a "$LOG_FILE"
        "$SCRIPT_DIR/health-check.sh"
        if [ $? -ne 0 ]; then
            echo "WARNING: Health check failed after rollback" | tee -a "$LOG_FILE"
        else
            echo "Health check passed" | tee -a "$LOG_FILE"
        fi
    fi
else
    echo "ERROR: Application failed to start after rollback" | tee -a "$LOG_FILE"
    echo "Check logs at $LOGS_DIR/app_$TIMESTAMP.log"
    exit 1
fi

echo "=== Rollback completed at $(date) ===" | tee -a "$LOG_FILE"
echo "Rollback successful. Application is running with PID: $NEW_PID"
echo "Log saved to: $LOG_FILE"



