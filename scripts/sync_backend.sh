#!/usr/bin/env bash
set -euo pipefail

SRC=/mnt/d/ai_block_project/.staging/backend
DST=/home/ubuntu/ai_block_project/backend/backend

cp -rf "$SRC/src/." "$DST/src/"
cp -rf "$SRC/sql/." "$DST/sql/"
cp -f "$SRC/pom.xml" "$DST/pom.xml"

echo "synced staging backend to $DST"
