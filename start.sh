#!/bin/bash

# Get the directory of this script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"

# Check if a PID file exists and if the process is still running
if [ -f springboot.pid ]; then
  PID=$(cat springboot.pid)
  if ps -p $PID > /dev/null; then
    echo "Server is already running with PID $PID"
    exit 1
  else
    echo "Removing stale PID file"
    rm springboot.pid
  fi
fi

echo "Starting server with Maven in foreground mode..."

# Start the server with the default profile in foreground
./mvnw spring-boot:run 