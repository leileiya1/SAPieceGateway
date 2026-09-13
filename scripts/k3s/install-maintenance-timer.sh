#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cat > /etc/systemd/system/sapiece-backup.service <<EOF
[Unit]
Description=SAPiece MySQL and Redis backup
After=k3s.service
Requires=k3s.service

[Service]
Type=oneshot
ExecStart=$repo_root/scripts/k3s/backup-data.sh
Nice=10
IOSchedulingClass=best-effort
IOSchedulingPriority=7
PrivateTmp=true
NoNewPrivileges=true
EOF

cat > /etc/systemd/system/sapiece-backup.timer <<'EOF'
[Unit]
Description=Daily SAPiece data backup

[Timer]
OnCalendar=*-*-* 02:30:00
RandomizedDelaySec=20m
Persistent=true
Unit=sapiece-backup.service

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now sapiece-backup.timer
systemctl list-timers sapiece-backup.timer --no-pager
