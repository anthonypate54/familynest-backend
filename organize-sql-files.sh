#!/bin/bash
# Script to organize SQL files into categorized directories

# Create directories if they don't exist
mkdir -p sql_references/queries
mkdir -p sql_references/tests
mkdir -p sql_references/performance
mkdir -p sql_references/backups

# Move SQL files from src/main to appropriate directories
echo "Moving SQL files from src/main to organized directories..."

# Move query-related files
mv src/main/*query*.sql sql_references/queries/ 2>/dev/null
mv src/main/*join*.sql sql_references/queries/ 2>/dev/null
mv src/main/*cte*.sql sql_references/queries/ 2>/dev/null
mv src/main/*working*.sql sql_references/queries/ 2>/dev/null

# Move test-related files
mv src/main/*test*.sql sql_references/tests/ 2>/dev/null
mv src/main/check_*.sql sql_references/tests/ 2>/dev/null

# Move performance-related files
mv src/main/resources/*optimized*.sql sql_references/performance/ 2>/dev/null
mv src/main/resources/performance_*.sql sql_references/performance/ 2>/dev/null

# Move backup/archive files
mv src/main/resources/db/migration_archive/*.sql sql_references/backups/ 2>/dev/null
mv src/main/resources/db/migration_backup/*.sql sql_references/backups/ 2>/dev/null
mv src/main/resources/*backup*.sql sql_references/backups/ 2>/dev/null

# Create a README file explaining the organization
cat > sql_references/README.md << EOL
# SQL Files Reference

This directory contains SQL files that are useful for reference but don't need to be in version control.

## Directory Structure

- **queries**: SQL query examples and patterns that might be useful for future development
- **tests**: SQL files used for testing database functionality
- **performance**: SQL files related to performance optimization
- **backups**: Backup and archive files, including old migrations

## Management

These files are excluded from Git using patterns in the project's .gitignore file.
If you need to add a new SQL file that should be version controlled (like a new migration),
make sure to place it in the appropriate location (src/main/resources/db/migration/).
EOL

echo "Done organizing SQL files."
echo "SQL files are now organized in the sql_references directory."
echo "Check sql_references/README.md for more information." 