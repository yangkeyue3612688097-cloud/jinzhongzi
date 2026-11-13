#!/usr/bin/env bash
set -euo pipefail

pkill -f "spring-boot:run" || true
pkill -f "org.example.backend.BackendApplication" || true
rm -f /home/ubuntu/ai_block_project/backend.pid
echo "backend stopped"
