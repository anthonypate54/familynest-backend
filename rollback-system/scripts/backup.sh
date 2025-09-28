#!/bin/bash
# backup.sh - Creates backups of Spring Boot application and configurations
# Usage: ./backup.sh [version_tag]

# Set variables
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="/Users/Anthony/projects/familynest-project/familynest-backend"
VERSIONS_DIR="$BASE_DIR/versions"
CONFIGS_DIR="$BASE_DIR/configs"
LOGS_DIR="$BASE_DIR/logs"

# Create timestamp
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
VERSION_TAG=${1:-$TIMESTAMP}

# Create directories if they don't exist
mkdir -p "$VERSIONS_DIR"
mkdir -p "$CONFIGS_DIR/$VERSION_TAG"
mkdir -p "$LOGS_DIR"

# Log file
LOG_FILE="$LOGS_DIR/backup_$TIMESTAMP.log"

# Start logging
echo "=== Backup started at $(date) ===" | tee -a "$LOG_FILE"
echo "Version tag: $VERSION_TAG" | tee -a "$LOG_FILE"

# Find the latest JAR file in the target directory
echo "Looking for JAR files in $APP_DIR/target/" | tee -a "$LOG_FILE"
LATEST_JAR=$(find "$APP_DIR/target" -name "*.jar" -type f -printf "%T@ %p\n" | sort -n | tail -1 | cut -d' ' -f2-)

if [ -z "$LATEST_JAR" ]; then
    echo "ERROR: No JAR file found in $APP_DIR/target/" | tee -a "$LOG_FILE"
    exit 1
fi

echo "Found JAR file: $LATEST_JAR" | tee -a "$LOG_FILE"

# Create a versioned copy of the JAR
JAR_FILENAME=$(basename "$LATEST_JAR")
JAR_NAME="${JAR_FILENAME%.*}"
VERSIONED_JAR="$VERSIONS_DIR/${JAR_NAME}-${VERSION_TAG}.jar"

echo "Creating backup of JAR to $VERSIONED_JAR" | tee -a "$LOG_FILE"
cp "$LATEST_JAR" "$VERSIONED_JAR"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to copy JAR file" | tee -a "$LOG_FILE"
    exit 1
fi

# Backup configuration files
echo "Backing up configuration files" | tee -a "$LOG_FILE"
cp "$APP_DIR/src/main/resources/application.properties" "$CONFIGS_DIR/$VERSION_TAG/" 2>/dev/null || echo "No application.properties found" | tee -a "$LOG_FILE"
cp "$APP_DIR/src/main/resources/application-*.properties" "$CONFIGS_DIR/$VERSION_TAG/" 2>/dev/null || echo "No application-*.properties found" | tee -a "$LOG_FILE"

# Create a metadata file with environment information
echo "Creating metadata file" | tee -a "$LOG_FILE"
cat > "$CONFIGS_DIR/$VERSION_TAG/metadata.txt" << EOL
Backup created: $(date)
Version tag: $VERSION_TAG
JAR file: $JAR_FILENAME
Java version: $(java -version 2>&1 | head -1)
System: $(uname -a)
EOL

# Create a symlink to the latest version
echo "Creating 'latest' symlink" | tee -a "$LOG_FILE"
ln -sf "$VERSIONED_JAR" "$VERSIONS_DIR/latest.jar"
ln -sf "$CONFIGS_DIR/$VERSION_TAG" "$CONFIGS_DIR/latest"

echo "=== Backup completed successfully at $(date) ===" | tee -a "$LOG_FILE"
echo "JAR backed up to: $VERSIONED_JAR"
echo "Configurations backed up to: $CONFIGS_DIR/$VERSION_TAG"
echo "Log saved to: $LOG_FILE"



