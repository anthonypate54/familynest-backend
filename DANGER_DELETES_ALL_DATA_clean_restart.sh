#!/bin/bash

# Warning and confirmation prompt
echo "WARNING: This script will DELETE ALL DATA in the familynest database!"
echo "Are you ABSOLUTELY SURE you want to proceed? (Type 'yes' to confirm)"
read -r confirmation

if [ "$confirmation" != "yes" ]; then
    echo "Operation cancelled. Database was not modified."
    exit 1
fi

echo "Stopping any running Spring Boot application..."
pkill -f "spring-boot:run" || true
# Give processes time to shut down
sleep 2

echo "Terminating all connections to the familynest database..."
PGPASSWORD=postgres psql -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'familynest' AND pid <> pg_backend_pid();"

echo "Dropping and recreating the database..."
PGPASSWORD=postgres psql -U postgres -c "DROP DATABASE IF EXISTS familynest;"
PGPASSWORD=postgres psql -U postgres -c "CREATE DATABASE familynest;"

echo "Starting Spring Boot application with clean schema..."
./mvnw clean spring-boot:run 