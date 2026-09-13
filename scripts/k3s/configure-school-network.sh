#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

# The local Meta/Clash TUN policy currently captures traffic to ubuntu-server.
# K3s node-to-node traffic must use the physical campus route instead.
cat > /etc/systemd/system/k3s-campus-route.service <<'UNIT'
[Unit]
Description=Route K3s traffic to ubuntu-server outside the Meta TUN
Before=k3s.service
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/bin/sh -c '/usr/sbin/ip rule show | grep -q "100:.*to 10.65.0.0/16 lookup main" || /usr/sbin/ip rule add priority 100 to 10.65.0.0/16 lookup main'
ExecStart=/bin/sh -c '/usr/sbin/ip rule show | grep -q "101:.*to 10.42.0.0/16 lookup main" || /usr/sbin/ip rule add priority 101 to 10.42.0.0/16 lookup main'
ExecStart=/bin/sh -c '/usr/sbin/ip rule show | grep -q "102:.*to 10.43.0.0/16 lookup main" || /usr/sbin/ip rule add priority 102 to 10.43.0.0/16 lookup main'
ExecStop=-/usr/sbin/ip rule del priority 102 to 10.43.0.0/16 lookup main
ExecStop=-/usr/sbin/ip rule del priority 101 to 10.42.0.0/16 lookup main
ExecStop=-/usr/sbin/ip rule del priority 100 to 10.65.0.0/16 lookup main
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable k3s-campus-route.service
systemctl restart k3s-campus-route.service
ip route get 10.65.13.94
ip route get 10.43.0.10
echo "school-linux K3s route is ready."
