#!/bin/bash

# Script to run the message preferences integration tests

set -e

echo "===== Running Message Preferences Integration Tests ====="
echo "Starting test run at $(date)"

# Change to the project root directory
cd "$(dirname "$0")/../../.."

# Ensure we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "Error: pom.xml not found. Please run this script from the project root directory."
    exit 1
fi

# Clean and compile the project first
echo "Compiling project..."
mvn clean compile -DskipTests

# Run just the message preferences tests
echo "Running tests..."
mvn test -Dtest=MessagePreferencesIntegrationTest

# Check if tests passed
if [ $? -eq 0 ]; then
    echo "✅ Message Preferences Tests PASSED"
else
    echo "❌ Message Preferences Tests FAILED"
    exit 1
fi

echo "Test run completed at $(date)" 