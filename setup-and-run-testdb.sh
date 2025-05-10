#!/bin/bash
# Combined script to set up FamilyNest test database and run it with performance optimizations

# Ensure we're in the correct directory
cd "$(dirname "$0")"
echo "Running from $(pwd)"

echo "==============================================================="
echo "STEP 1: Setting up a fresh test database"
echo "==============================================================="

# Run the setup script with modifications to avoid password hashing issues
./setup-testdb.sh

# Verify that the heavy user was created properly
echo "Verifying heavy user creation..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test -c "SELECT id, username, email FROM app_user WHERE id = 888;"

# If heavy user doesn't exist, run the fix script
if [ $? -ne 0 ] || [ "$(PGPASSWORD=postgres psql -tAc "SELECT COUNT(*) FROM app_user WHERE id = 888" familynest_test)" -eq "0" ]; then
  echo "Heavy user not found. Running fix script..."
  ./scripts/fix_heavy_user.sh
fi

echo "==============================================================="
echo "STEP 2: Setting up PostgreSQL performance monitoring"
echo "==============================================================="

# Enable PostgreSQL statistics tracking
./scripts/enable_pg_stats.sh

echo "==============================================================="
echo "STEP 3: Starting application with performance optimizations"
echo "==============================================================="

# Kill any existing Java processes that might be using port 8080
echo "Killing any existing Java processes..."
pkill -f java

# Kill any process using port 8080
echo "Killing any process using port 8080..."
lsof -ti:8080 | xargs kill -9 2>/dev/null || echo "No process using port 8080"

# Wait a moment to ensure ports are released
sleep 2

# Set environment variables for performance
export SPRING_PROFILES_ACTIVE=testdb
export JVM_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Start with performance tuning JVM options and testdb profile
echo "Starting application with testdb profile and performance optimizations..."
./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb -Dspring-boot.run.jvmArguments="$JVM_OPTS" &

# Wait for the application to start
echo "Waiting for application to start..."
sleep 10

echo "==============================================================="
echo "STEP 4: Running performance tests"
echo "==============================================================="

# Run performance tests
./scripts/performance_test.sh

echo ""
echo "Setup complete and application is running with performance optimizations."
echo "API is available at http://localhost:8080/api"
echo "To run performance tests again: ./scripts/performance_test.sh"
echo "To stop the application: pkill -f java"
echo ""
echo "For logging, check server.log or use: tail -f logs/familynest.log" 