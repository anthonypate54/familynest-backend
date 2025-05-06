# FamilyNest Testing Environment

This document describes how to set up and use the testing environment for the FamilyNest application.

## Database Setup

The application has two database configurations:

1. **Main Database**: `familynest` (PostgreSQL)
   - Used for normal development and production
   - Configured in `application.properties`

2. **Test Database**: `familynest_test` (PostgreSQL)
   - Used for UI testing with large datasets
   - Configured in `application-testdb.properties`
   - Contains test data with 100+ users, 20+ families, and complex relationships

## Setting Up the Test Environment

### Option 1: Using the setup script

```bash
# From the familynest-backend directory
./scripts/setup_and_run_testdb.sh
```

This script:
1. Creates the test database
2. Loads the test data
3. Starts the application with the test profile

For a custom port (if port 8080 is occupied):
```bash
./scripts/setup_and_run_testdb.sh --port 8081
```

### Option 2: Manual setup

```bash
# Create the database
psql -U postgres -f scripts/create_test_db.sql

# Load test data
psql -U postgres -d familynest_test -f src/main/resources/large-ui-dataset.sql

# Run the application with test profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb
```

## Running the Application

### With Main Database
```bash
./mvnw spring-boot:run
```

### With Test Database
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb
```

## Available Test Endpoints

- View test data status: `http://localhost:8080/api/test/status`

## Connecting with DBeaver

You can connect to either database using DBeaver:

### Main Database
- Host: localhost
- Port: 5432
- Database: familynest
- Username: postgres
- Password: postgres

### Test Database
- Host: localhost
- Port: 5432
- Database: familynest_test
- Username: postgres
- Password: postgres 