#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

namespace="${NAMESPACE:-sapiece}"
backup_root="${BACKUP_DIR:-/var/backups/sapiece}"
retention_days="${BACKUP_RETENTION_DAYS:-14}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
destination="$backup_root/$timestamp"
mkdir -p "$destination"
chmod 700 "$backup_root" "$destination"

k3s kubectl -n "$namespace" exec statefulset/mysql -- sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --quick --routines --events --all-databases' \
  | gzip -9 > "$destination/mysql-all.sql.gz"
gzip -t "$destination/mysql-all.sql.gz"

k3s kubectl -n "$namespace" exec statefulset/redis -- sh -c \
  'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning SAVE >/dev/null'
k3s kubectl -n "$namespace" cp redis-0:/data/dump.rdb "$destination/redis-dump.rdb"
test -s "$destination/redis-dump.rdb"

k3s kubectl get namespace,deploy,statefulset,service,pdb,hpa,networkpolicy -A -o yaml \
  > "$destination/kubernetes-resources.yaml"
(cd "$destination" && sha256sum mysql-all.sql.gz redis-dump.rdb kubernetes-resources.yaml > SHA256SUMS)

find "$backup_root" -mindepth 1 -maxdepth 1 -type d -mtime "+$retention_days" -exec rm -rf {} +
ln -sfn "$destination" "$backup_root/latest"

if [[ -n "${BACKUP_RSYNC_TARGET:-}" ]]; then
  rsync -a --delete-delay "$destination/" "${BACKUP_RSYNC_TARGET%/}/$timestamp/"
fi

echo "Backup complete: $destination"
