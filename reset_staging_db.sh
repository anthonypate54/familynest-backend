#!/bin/bash

# Script to completely reset the FamilyNest staging database on AWS
# This will drop all tables and let Flyway recreate everything from scratch

echo "🗑️  Resetting FamilyNest staging database..."

# Extract database details from environment variables
DB_URL="${JDBC_DATABASE_URL}"
DB_USER="${JDBC_DATABASE_USERNAME}"
DB_PASSWORD="${JDBC_DATABASE_PASSWORD}"

if [ -z "$DB_URL" ] || [ -z "$DB_USER" ] || [ -z "$DB_PASSWORD" ]; then
    echo "❌ Database environment variables not set!"
    echo "Please ensure these are set:"
    echo "   JDBC_DATABASE_URL"
    echo "   JDBC_DATABASE_USERNAME" 
    echo "   JDBC_DATABASE_PASSWORD"
    exit 1
fi

# Extract database name and host from JDBC URL
# Format: jdbc:postgresql://host:port/database
DB_HOST=$(echo "$DB_URL" | sed 's/.*:\/\/\([^:]*\):.*/\1/')
DB_PORT=$(echo "$DB_URL" | sed 's/.*:\([0-9]*\)\/.*/\1/')
DB_NAME=$(echo "$DB_URL" | sed 's/.*\/\([^?]*\).*/\1/')

echo "📋 Database details:"
echo "   Host: $DB_HOST:$DB_PORT"
echo "   Database: $DB_NAME"
echo "   User: $DB_USER"
echo ""

# Function to execute SQL commands
execute_sql() {
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "$1"
}

echo "🔌 Testing database connection..."
if ! PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "SELECT 1;" &> /dev/null; then
    echo "❌ Cannot connect to PostgreSQL. Please check:"
    echo "   - Database host is accessible"
    echo "   - Credentials are correct"
    echo "   - User has proper permissions"
    exit 1
fi

echo "✅ Database connection successful"
echo ""

echo "⚠️  WARNING: This will completely destroy all data in the '$DB_NAME' database!"
echo "Press Enter to continue or Ctrl+C to cancel..."
read

echo "🗑️  Dropping database '$DB_NAME'..."
execute_sql "DROP DATABASE IF EXISTS \"$DB_NAME\";"

echo "🆕 Creating fresh database '$DB_NAME'..."
execute_sql "CREATE DATABASE \"$DB_NAME\";"

echo "✅ Database reset complete!"
echo ""
echo "🔄 Now restart the Spring Boot application to let Flyway recreate all tables:"
echo "   ./kill-spring.sh && ./start-spring.sh"
echo ""
echo "📱 Fresh database ready for testing!"


