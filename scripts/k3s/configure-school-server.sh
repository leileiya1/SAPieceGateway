#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

backup_root="${K3S_BACKUP_ROOT:-/home/sapiece/sapiece-gateway-backups}"
backup_dir="$backup_root/$(date +%Y%m%d-%H%M%S)-k3s-server"
install -d -m 0700 "$backup_dir"

systemctl stop k3s
trap 'systemctl start k3s >/dev/null 2>&1 || true' EXIT

tar -C /var/lib/rancher/k3s/server -czf "$backup_dir/server-db-and-token.tar.gz" db token node-token 2>/dev/null || \
  tar -C /var/lib/rancher/k3s/server -czf "$backup_dir/server-db-and-token.tar.gz" db token
systemctl cat k3s > "$backup_dir/k3s.service.txt"
if [[ -f /etc/rancher/k3s/config.yaml ]]; then
  cp -a /etc/rancher/k3s/config.yaml "$backup_dir/config.yaml.before"
fi

install -d -m 0755 /etc/rancher/k3s
cat > /etc/rancher/k3s/resolv.conf <<'EOF'
nameserver 10.64.0.134
nameserver 202.195.241.234
options timeout:2 attempts:2
EOF

cat > /etc/rancher/k3s/config.yaml <<'EOF'
write-kubeconfig-mode: "0640"
node-ip: 10.70.239.17
advertise-address: 10.70.239.17
tls-san:
  - 10.70.239.17
flannel-iface: eno1
resolv-conf: /etc/rancher/k3s/resolv.conf
secrets-encryption: true
disable:
  - traefik
  - servicelb
EOF

systemctl start k3s
trap - EXIT

for _ in $(seq 1 60); do
  if k3s kubectl get --raw=/readyz >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
k3s kubectl get --raw=/readyz >/dev/null
for _ in $(seq 1 15); do
  if k3s secrets-encrypt status >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
k3s secrets-encrypt status
echo "K3s server configured. Backup: $backup_dir"
