#!/bin/bash

# Script to completely reset the local FamilyNest database
# This will drop all tables and let Flyway recreate everything from scratch

echo "🗑️  Resetting FamilyNest local database..."

# Database connection details (adjust if your setup is different)
DB_NAME="familynest_test"
DB_USER="postgres"
DB_HOST="localhost"
DB_PORT="5432"

echo "📋 Database details:"
echo "   Host: $DB_HOST:$DB_PORT"
echo "   Database: $DB_NAME"
echo "   User: $DB_USER"
echo ""

# Function to execute SQL commands
execute_sql() {
    psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "$1"
}

echo "🔌 Testing database connection..."
if ! psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "SELECT 1;" &> /dev/null; then
    echo "❌ Cannot connect to PostgreSQL. Please ensure:"
    echo "   - PostgreSQL is running"
    echo "   - Connection details are correct"
    echo "   - User has proper permissions"
    exit 1
fi

echo "✅ Database connection successful"
echo ""

echo "⚠️  WARNING: This will completely destroy all data in the '$DB_NAME' database!"
echo "Press Enter to continue or Ctrl+C to cancel..."
read

echo "🗑️  Dropping database '$DB_NAME'..."
execute_sql "DROP DATABASE IF EXISTS $DB_NAME;"

echo "🆕 Creating fresh database '$DB_NAME'..."
execute_sql "CREATE DATABASE $DB_NAME;"

echo "✅ Database reset complete!"
echo ""
echo "🔄 Now run the Spring Boot application to let Flyway recreate all tables:"
echo "   ./mvnw spring-boot:run"
echo ""
echo "📱 After that, you can test the new member bug reproduction."
