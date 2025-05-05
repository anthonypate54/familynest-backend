#!/bin/bash

# Script to run social engagement feature tests for FamilyNest

# Set the working directory to the project root
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR" || { echo "Error: Could not change to project directory"; exit 1; }

echo "===== Running Social Engagement Feature Tests ====="
echo "Project directory: $PROJECT_DIR"

# Check if Maven is installed
if ! command -v ./mvnw &> /dev/null; then
    echo "Error: Maven wrapper not found. Please run this script from the project root."
    exit 1
fi

# Run the specific test classes for social engagement features
./mvnw test -Dtest=ReactionControllerIntegrationTest,ViewTrackingControllerIntegrationTest

# Check if tests were successful
if [ $? -eq 0 ]; then
    echo "===== All social engagement tests passed successfully! ====="
    exit 0
else
    echo "===== Some social engagement tests failed! Check the logs above. ====="
    exit 1
fi 