#!/bin/bash

# This script sets up log rotation for FamilyNest backend logs on AWS
# Specifically targeting the nohup.out file used for application output

# Exit on any error
set -e

echo "Setting up log rotation for FamilyNest AWS logs..."

# Check if running as root
if [ "$(id -u)" -ne 0 ]; then
    echo "This script must be run as root or with sudo"
    exit 1
fi

# Define the application directory - update this to match your actual directory
APP_DIR="/home/ubuntu/familynest-backend"

# Create logrotate configuration file
cat > /etc/logrotate.d/familynest << EOF
# Rotate the main nohup.out file
$APP_DIR/nohup.out {
    daily
    rotate 5
    compress
    delaycompress
    missingok
    notifempty
    create 0644 ubuntu ubuntu
    dateext
    dateformat -%Y-%m-%d
    copytruncate
}

# Also rotate any other log files in the logs directory
$APP_DIR/logs/*.log {
    daily
    rotate 5
    compress
    delaycompress
    missingok
    notifempty
    create 0644 ubuntu ubuntu
    dateext
    dateformat -%Y-%m-%d
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
echo ""
echo "You can continue to use 'tail -f nohup.out' to monitor your application"




