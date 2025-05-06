#!/bin/bash

# Script to set up the familynest_test database and run the application with it
# 
# USAGE:
#   ./scripts/setup_and_run_testdb.sh [--port PORT]
#
# NOTES:
#   - Run this script from the familynest-backend directory
#   - Must have PostgreSQL installed and running
#   - Creates and populates a PostgreSQL database called familynest_test
#   - Uses the regular postgres user (no special user creation needed)
#   - Data can be loaded by directly running the SQL script:
#     psql -U postgres -d familynest_test -f src/main/resources/large-ui-dataset.sql

# Default port
PORT=8080

# Process command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -p|--port)
      PORT="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [-p|--port PORT]"
      exit 1
      ;;
  esac
done

echo "Setting up FamilyNest Test Database environment"
echo "----------------------------------------------"

# Check if PostgreSQL client is installed
if ! command -v psql &> /dev/null; then
    echo "PostgreSQL client is not installed. Please install it first."
    exit 1
fi

# Check if PostgreSQL server is running
echo "Checking PostgreSQL connection..."
if ! pg_isready -h localhost -p 5432 > /dev/null 2>&1; then
    echo "PostgreSQL server is not running. Please start it first."
    exit 1
fi

echo "PostgreSQL connection successful."

# Create the test database
echo "Creating test database..."
# Try first without password
if psql -U postgres -c "SELECT 'Connection successful' AS message;" &> /dev/null; then
    echo "Creating database as postgres user..."
    psql -U postgres -f scripts/create_test_db.sql
else
    # If that fails, try with password prompt
    echo "Enter PostgreSQL superuser (postgres) password:"
    read -s PGPASSWORD
    export PGPASSWORD
    
    # Test connection with password
    if ! psql -U postgres -c "SELECT 'Connection successful' AS message;" &> /dev/null; then
        echo "Failed to connect to PostgreSQL. Check your credentials."
        unset PGPASSWORD
        exit 1
    fi
    
    psql -U postgres -f scripts/create_test_db.sql
    unset PGPASSWORD
fi

if [ $? -ne 0 ]; then
    echo "Failed to create test database. Check PostgreSQL permissions."
    exit 1
fi

echo "Test database created successfully."

# Load the test data directly
echo "Loading test data..."
psql -U postgres -d familynest_test -f src/main/resources/large-ui-dataset.sql

# Run the application with testdb profile
echo "Running the application with testdb profile on port $PORT..."
echo "The application will be available at http://localhost:$PORT"
echo "The H2 console will be available at http://localhost:$PORT/h2-console"
echo "To check data status: http://localhost:$PORT/api/test/status"
echo ""
echo "Press Ctrl+C to stop the application"

# Build and run in a single command - skip tests to speed up
./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb -Dspring-boot.run.arguments="--server.port=$PORT" -DskipTests

# Note: You'll need to manually press Ctrl+C to stop the application 