#!/usr/bin/env bash
set -euo pipefail

backup_dir="${1:-$HOME/sapiece-gateway-backups/$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"

docker exec sapiece-gateway-mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --events sapiece_gateway' \
  > "$backup_dir/sapiece_gateway.sql"

docker exec sapiece-gateway-redis sh -c \
  'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --rdb /tmp/sapiece-gateway.rdb >/dev/null'
docker cp sapiece-gateway-redis:/tmp/sapiece-gateway.rdb "$backup_dir/redis.rdb" >/dev/null
docker exec sapiece-gateway-redis rm -f /tmp/sapiece-gateway.rdb

sha256sum "$backup_dir/sapiece_gateway.sql" "$backup_dir/redis.rdb" > "$backup_dir/SHA256SUMS"
chmod 600 "$backup_dir"/*
printf 'Backup created: %s\n' "$backup_dir"
