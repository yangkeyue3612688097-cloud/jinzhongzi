#!/usr/bin/env bash
set -euo pipefail

base=/mnt/d/ai_block_project/scripts

bash "$base/sync_backend.sh"
cd /home/ubuntu/ai_block_project/backend/backend
./mvnw -q -DskipTests compile
bash "$base/stop_backend.sh"
bash "$base/start_backend.sh"

echo "backend rebuilt and restarting"
