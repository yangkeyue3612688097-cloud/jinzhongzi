#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_PASSWORD:?请先设置 MYSQL_PASSWORD 环境变量}"
MYSQL=(mysql -h127.0.0.1 -P3307 -uroot -p"${MYSQL_PASSWORD}" --default-character-set=utf8mb4)
SQL_DIR=/home/ubuntu/ai_block_project/backend/backend/sql
DB=aigc_evidence

"${MYSQL[@]}" -e "CREATE DATABASE IF NOT EXISTS $DB DEFAULT CHARACTER SET utf8mb4"

for file in \
    upgrade_20261030_generation_evidence.sql \
    upgrade_20261030_comfyui_order_audit.sql \
    upgrade_20261030_collaboration_agreement.sql \
    upgrade_20261030_competition.sql; do
    echo "applying $file"
    "${MYSQL[@]}" "$DB" < "$SQL_DIR/$file"
done

echo "backend upgrades applied"
