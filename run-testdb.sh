#!/bin/bash
# Run FamilyNest with testdb profile including performance optimizations

# Ensure we're in the correct directory
cd "$(dirname "$0")"
echo "Running from $(pwd)"

# Kill any existing Java processes that might be using port 8080
echo "Killing any existing Java processes..."
pkill -f java

# Kill any process using port 8080
echo "Killing any process using port 8080..."
lsof -ti:8080 | xargs kill -9 2>/dev/null || echo "No process using port 8080"

# Wait a moment to ensure ports are released
sleep 2

# Enable PostgreSQL statistics tracking
echo "Enabling PostgreSQL performance monitoring..."
./scripts/enable_pg_stats.sh

# Clean and rebuild the application
echo "Cleaning and rebuilding application..."
./mvnw clean package -DskipTests

# Apply performance indexes before starting the application
echo "Applying performance indexes to the database..."
PGPASSWORD=postgres psql -U postgres -d familynest_test -f src/main/resources/performance_indexes.sql

# Start the application with the testdb profile
echo "Starting application with testdb profile and performance optimizations..."
export SPRING_PROFILES_ACTIVE=testdb
export JVM_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Start with performance tuning JVM options and testdb profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb -Dspring-boot.run.jvmArguments="$JVM_OPTS" &

# Wait for the application to start
echo "Waiting for application to start..."
sleep 10

# Run performance tests
echo "Running performance tests..."
./scripts/performance_test.sh

echo ""
echo "Application is running with performance optimizations."
echo "API is available at http://localhost:8080/api"
echo "To run performance tests again: ./scripts/performance_test.sh"
echo "To stop the application: pkill -f java" 