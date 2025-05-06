#!/bin/bash

# Query user family relationships directly from the database
# This script runs SQL queries to verify the exact relationships between users and families

# Configurable settings
DB_USER=${1:-"family_nest_user"}  # First argument or default
DB_PASSWORD=${2:-"family_nest_password"}  # Second argument or default
DB_NAME=${3:-"family_nest_db"}  # Third argument or default
DB_HOST=${4:-"localhost"}  # Fourth argument or default
USER_ID=${5:-1}  # Fifth argument or default user ID to query

# Check if mysql client is available
if ! command -v mysql &> /dev/null; then
    echo "MySQL client is not installed. Please install it to run this script."
    exit 1
fi

# Print the config being used
echo "=== Database Connection ==="
echo "Host: $DB_HOST"
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo "Querying for user ID: $USER_ID"
echo "=========================="
echo ""

# Function to run a query and format the output
run_query() {
    local query="$1"
    local header="$2"
    
    echo "=== $header ==="
    mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "$query" --table
    echo "------------------------"
    echo ""
}

echo "Querying family relationships for user ID: $USER_ID"
echo ""

# Replace the placeholder user ID with the actual user ID in the SQL file
SQL_TEMP=$(mktemp)
cat query_user_family_data.sql | sed "s/user_id = 1/user_id = $USER_ID/g" | sed "s/created_by = 1/created_by = $USER_ID/g" | sed "s/u.id != 1/u.id != $USER_ID/g" > "$SQL_TEMP"

# Run each query separately with a header
run_query "$(sed -n '/-- 1\. Get all/,/;/p' "$SQL_TEMP")" "Families Associated with User $USER_ID"

run_query "$(sed -n '/-- 2\. Get all direct/,/;/p' "$SQL_TEMP")" "All Family Members Across User's Families"

run_query "$(sed -n '/-- 3\. Get message preferences/,/;/p' "$SQL_TEMP")" "User's Message Preference Settings"

run_query "$(sed -n '/-- 4\. For each family/,/;/p' "$SQL_TEMP")" "Families Owned by User's Family Members"

run_query "$(sed -n '/-- 5\. Summary/,/;/p' "$SQL_TEMP")" "Summary Statistics"

# Clean up temp file
rm "$SQL_TEMP"

# Get user details for context
echo "=== User Details ==="
mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SELECT id, username, first_name, last_name, email, role FROM app_user WHERE id = $USER_ID" --table
echo "------------------------" 