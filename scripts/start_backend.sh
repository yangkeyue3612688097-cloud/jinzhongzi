#!/usr/bin/env bash
set -euo pipefail

cd /home/ubuntu/ai_block_project/backend/backend

if pgrep -f "org.example.backend.BackendApplication" > /dev/null 2>&1; then
    echo "backend already running"
    exit 0
fi

nohup ./mvnw -q -o -DskipTests spring-boot:run \
    > /home/ubuntu/ai_block_project/backend-run.log 2>&1 < /dev/null &
echo $! > /home/ubuntu/ai_block_project/backend.pid
echo "backend starting, mvn pid $(cat /home/ubuntu/ai_block_project/backend.pid)"
