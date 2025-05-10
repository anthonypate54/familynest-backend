# FamilyNest Test Database Setup

This document explains how to set up and use the FamilyNest test database for development and testing.

## Overview

The test database (`familynest_test`) contains a pre-populated dataset with:
- Multiple users with fixed test passwords
- Multiple families with relationships
- ~50 message threads with ~200 total messages
- A large thread with 1000+ messages for load testing
- Message views, reactions, and comments for metrics testing

## Quick Setup

Run the setup script to create and populate the test database:

```bash
# From the familynest-backend directory
./setup-testdb.sh
```

## Manual Setup

If you prefer to set up the database manually:

1. Create the test database:
```bash
psql -U postgres -c "DROP DATABASE IF EXISTS familynest_test;"
psql -U postgres -c "CREATE DATABASE familynest_test;"
```

2. Run the schema initialization:
```bash
psql -U postgres -d familynest_test -f src/main/resources/schema-testdb.sql
```

3. Populate the database with test data:
```bash
psql -U postgres -d familynest_test -f src/main/resources/data-testdb.sql
```

## Running the Application with Test Database

To start the application using the test database from the backend directory:

```bash
cd familynest-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb
```

## Flutter App Testing

When testing with the Flutter app, use this dedicated test user:

```
Email: testuser@example.com
Password: user2123
```

The testuser account has been set up with the same password hash as user2 from the main database, ensuring consistent login behavior. This user has access to the family that contains the large thread for testing metrics.

When the Flutter app configuration is set to use localhost:8080, it will connect to the test database when the backend is running with the testdb profile.

## Testing API Endpoints

The application uses JWT tokens for authentication. You can test the API endpoints in two ways:

### Option 1: Using an existing token

Use a known JWT token for testing:

```bash
# Test message engagement metrics
curl -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMDEiLCJyb2xlIjoiVVNFUiIsImV4cCI6MTc0NjY1MDI2OX0.S-kbcG3qS6C8UgiN4GVLtjwbloHZda5c5qfG76WBCqnOWKEnowA47grgz9YaIKn0H0FLzbboB4CAm4GIwbgjXw" \
  http://localhost:8080/api/messages/102/engagement
```

This token is pre-configured to work with the test database and will authenticate as user ID 101.

### Option 2: Direct database queries

You can query the metrics data directly from the database:

```bash
# View message engagement statistics
psql -U postgres -d familynest_test -c "SELECT m.id, 
    (SELECT COUNT(*) FROM message_view WHERE message_id = m.id) AS views,
    (SELECT COUNT(*) FROM message_reaction WHERE message_id = m.id) AS reactions,
    (SELECT COUNT(*) FROM message_comment WHERE message_id = m.id) AS comments
  FROM message m WHERE m.id = 102;"
```

## Finding the Large Thread

The test database includes a large thread with 1000 messages distributed across multiple users for load testing. To find this thread:

### Finding the thread starter message:

```bash
# Get the thread starter information
psql -U postgres -d familynest_test -c "SELECT id, family_id, sender_id, sender_username, content 
  FROM message WHERE content LIKE 'This is the start of our load testing thread%';"
```

### Viewing message distribution by user:

```bash
# See how many messages each user contributed to the thread
psql -U postgres -d familynest_test -c "SELECT sender_username, COUNT(*) as message_count 
  FROM message 
  WHERE family_id = (SELECT family_id FROM message WHERE content LIKE 'This is the start of our load testing thread%') 
  GROUP BY sender_username 
  ORDER BY message_count DESC;"
```

### Getting messages from a specific user in the thread:

```bash
# Get the first 10 messages from a specific user in the thread
psql -U postgres -d familynest_test -c "SELECT id, content, timestamp 
  FROM message 
  WHERE family_id = (SELECT family_id FROM message WHERE content LIKE 'This is the start of our load testing thread%')
  AND sender_username = 'charlie.brown'
  ORDER BY timestamp
  LIMIT 10;"
```

## Test Data Details

The test database includes:

1. **Users**: 
   - Regular test users with varied profiles
   - A special user (testuser@example.com) with password "user2123" for Flutter testing
2. **Families**: 3 families with different sizes and relationships
3. **Messages**: 
   - ~50 message threads with varied engagement
   - 1 large thread with 1000+ messages for load testing
4. **Metrics Data**:
   - Message views (who viewed and when)
   - Reactions (likes, loves, etc.)
   - Comments on messages

## Test Database Configuration

The test database configuration is defined in:
- `application-testdb.properties` - Database connection and initialization settings
- `schema-testdb.sql` - Schema initialization
- `data-testdb.sql` - Master script that loads all test data
- `test-data-large.sql` - Users and families data
- `test-messages.sql` - Regular message threads
- `test-large-thread.sql` - Single large thread for load testing

## Troubleshooting

If you encounter issues with the test database:

1. Make sure PostgreSQL is running: `pg_isready`
2. Check if the database exists: `psql -U postgres -c "\l" | grep familynest_test`
3. Verify database connections: `ps aux | grep familynest_test`
4. Review logs in the application for database connectivity issues 