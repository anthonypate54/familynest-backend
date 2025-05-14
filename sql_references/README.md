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
