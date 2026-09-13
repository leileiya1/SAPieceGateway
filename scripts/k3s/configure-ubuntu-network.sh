#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

cat > /etc/systemd/system/k3s-campus-route.service <<'UNIT'
[Unit]
Description=Route K3s traffic outside the Meta TUN
Before=k3s-agent.service
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/bin/sh -c '/usr/sbin/ip rule show | grep -q "100:.*to 10.70.224.0/20 lookup main" || /usr/sbin/ip rule add priority 100 to 10.70.224.0/20 lookup main'
ExecStart=/bin/sh -c '/usr/sbin/ip rule show | grep -q "101:.*to 10.42.0.0/16 lookup main" || /usr/sbin/ip rule add priority 101 to 10.42.0.0/16 lookup main'
ExecStart=/bin/sh -c '/usr/sbin/ip rule show | grep -q "102:.*to 10.43.0.0/16 lookup main" || /usr/sbin/ip rule add priority 102 to 10.43.0.0/16 lookup main'
ExecStop=-/usr/sbin/ip rule del priority 102 to 10.43.0.0/16 lookup main
ExecStop=-/usr/sbin/ip rule del priority 101 to 10.42.0.0/16 lookup main
ExecStop=-/usr/sbin/ip rule del priority 100 to 10.70.224.0/20 lookup main
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable k3s-campus-route.service
systemctl restart k3s-campus-route.service

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q '^Status: active'; then
  ufw allow from 10.70.239.17 to any port 8472 proto udp comment 'K3s VXLAN school-linux'
  ufw allow from 10.70.239.17 to any port 10250 proto tcp comment 'K3s kubelet school-linux'
  ufw allow from 10.0.0.0/8 to any port 30096 proto tcp comment 'SAPiece Gateway NodePort'
fi

ip route get 10.70.239.17
ip route get 10.43.0.10
echo "ubuntu-server K3s route is ready."
