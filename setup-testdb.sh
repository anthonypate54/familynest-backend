#!/bin/bash
# Test Database Setup Script for FamilyNest

echo "Setting up FamilyNest test database..."

# Disable PostgreSQL paging completely
export PAGER=""
export PGPAGER=""
export PSQL_PAGER=""

# Stop any running application using the database
echo "Stopping any processes using the database..."
# This part is commented out but can be uncommented if needed
ps aux | grep '[s]pring-boot' | grep testdb | awk '{print $2}' | xargs kill -9 2>/dev/null || true

# Terminate all connections to the test database
echo "Terminating database connections..."
psql -X -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'familynest_test';" > /dev/null 2>&1

# Drop and recreate the database
echo "Recreating the database..."
psql -X -U postgres << EOF
DROP DATABASE IF EXISTS familynest_test;
CREATE DATABASE familynest_test;
EOF

# Run the application with JPA schema generation and then shut it down
echo "Generating database schema with JPA..."
echo "This will take a few seconds..."
./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb -Dspring.jpa.hibernate.ddl-auto=create -Dspring.sql.init.mode=never &
PID=$!

# Wait for the application to start
sleep 15

# Kill the application
kill $PID
sleep 2

# Connect to the database and run the initialization scripts
echo "Populating test data..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test -f src/main/resources/data-testdb.sql

# Display information about the large thread with clear formatting
echo ""
echo "======================= THREAD INFORMATION ======================="
echo "1. THREAD STARTER:"

# Use the -t flag (tuples only) to avoid displaying headers
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Thread ID: ' || id || ', Family: ' || family_id || ', Sender: ' || sender_username || ' (ID: ' || sender_id || ')' FROM message WHERE content LIKE 'This is the start of our load testing thread%';"
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Content: ' || content FROM message WHERE content LIKE 'This is the start of our load testing thread%';"

echo ""
echo "2. MESSAGE DISTRIBUTION BY USER:"

# Format the output for better visibility without tables
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  ' || sender_username || ': ' || message_count || ' messages' FROM (SELECT sender_username, COUNT(*) as message_count FROM message WHERE family_id = (SELECT family_id FROM message WHERE content LIKE 'This is the start of our load testing thread%') GROUP BY sender_username ORDER BY message_count DESC) as counts;"

echo "=================================================================="

echo ""
echo "Database setup complete!"
echo "To run the application with the test database:"
echo "./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb"
echo ""
echo "To test metrics data, use the pre-configured JWT token:"
echo "curl -H \"Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMDEiLCJyb2xlIjoiVVNFUiIsImV4cCI6MTc0NjY1MDI2OX0.S-kbcG3qS6C8UgiN4GVLtjwbloHZda5c5qfG76WBCqnOWKEnowA47grgz9YaIKn0H0FLzbboB4CAm4GIwbgjXw\" \\"
echo "  http://localhost:8080/api/messages/102/engagement"
echo ""
echo "Or query directly from the database:"
echo "psql -X -U postgres -d familynest_test -c \"SELECT COUNT(*) FROM message_view WHERE message_id = 102;\""
echo ""
echo "To find the large thread starter and distribution:"
echo "psql -X -U postgres -d familynest_test -c \"SELECT id, family_id, sender_id, sender_username FROM message WHERE content LIKE 'This is the start of our load testing thread%';\"" 