#!/bin/bash

# This script sets up log rotation for FamilyNest backend logs
# It creates a logrotate configuration file and adds it to the system's logrotate.d directory

# Exit on any error
set -e

echo "Setting up log rotation for FamilyNest logs..."

# Check if running as root
if [ "$(id -u)" -ne 0 ]; then
    echo "This script must be run as root or with sudo"
    exit 1
fi

# Define the log directory - update this to match your actual log directory
LOG_DIR="/home/ubuntu/familynest-backend/logs"

# Create logrotate configuration file
cat > /etc/logrotate.d/familynest << EOF
$LOG_DIR/*.log {
    daily
    rotate 5
    compress
    delaycompress
    missingok
    notifempty
    create 0640 ubuntu ubuntu
    dateext
    dateformat -%Y-%m-%d
    postrotate
        # Add commands to run after rotation if needed
        # For example, to signal the application to reopen log files
        # systemctl kill -s USR1 familynest.service
    endscript
}
EOF

echo "Logrotate configuration created at /etc/logrotate.d/familynest"

# Test the configuration
echo "Testing logrotate configuration..."
logrotate -d /etc/logrotate.d/familynest

echo "Log rotation setup complete."
echo "Logs will be rotated daily, keeping 5 days of history."
echo "Old logs will be compressed to save space."
echo ""
echo "To manually rotate logs, run: sudo logrotate -f /etc/logrotate.d/familynest"


