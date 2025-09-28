# Log Management for FamilyNest Backend

This document outlines the log management strategy for the FamilyNest backend running on AWS.

## Log Rotation Strategy

We use `logrotate` to manage log files and prevent them from consuming too much disk space. The current configuration:

- **Rotation Frequency**: Daily
- **Retention Period**: 5 days
- **Compression**: Yes (with delay of 1 day)
- **Naming Convention**: Original filename with date suffix (YYYY-MM-DD)

## Setup Instructions

1. Copy the `setup-logrotate.sh` script to your AWS server:
   ```bash
   scp setup-logrotate.sh ubuntu@your-aws-server-ip:~
   ```

2. SSH into your AWS server:
   ```bash
   ssh ubuntu@your-aws-server-ip
   ```

3. Run the setup script with sudo:
   ```bash
   sudo bash setup-logrotate.sh
   ```

## Manual Rotation

To manually trigger log rotation (for testing or immediate space recovery):

```bash
sudo logrotate -f /etc/logrotate.d/familynest
```

## Monitoring Log Size

To check the current size of log files:

```bash
du -h /home/ubuntu/familynest-backend/logs/
```

## Adjusting the Configuration

If you need to modify the log rotation settings:

1. Edit the configuration file:
   ```bash
   sudo nano /etc/logrotate.d/familynest
   ```

2. Common adjustments:
   - Change `daily` to `weekly` or `monthly` for less frequent rotation
   - Adjust `rotate 5` to keep more or fewer backups
   - Add `size 100M` to rotate based on size rather than time

## Spring Boot Application Configuration

For optimal log management with Spring Boot:

1. Configure your `application.properties` to use rolling file appenders:
   ```properties
   # Log to a file
   logging.file.name=/home/ubuntu/familynest-backend/logs/application.log
   
   # Set logging levels
   logging.level.root=INFO
   logging.level.com.familynest=DEBUG
   
   # Log pattern
   logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
   ```

2. Consider implementing log file reopening on SIGUSR1 signals to work with logrotate's `postrotate` directive.

## Best Practices

1. **Regular Monitoring**: Check log sizes weekly
2. **Adjust as Needed**: Increase/decrease retention based on disk usage
3. **Log Levels**: Use appropriate log levels to reduce verbosity in production
4. **Archived Logs**: Consider setting up a process to archive important logs to S3 for longer-term retention if needed




