#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

server_url="${K3S_URL:-https://10.70.239.17:6443}"
node_ip="${K3S_NODE_IP:-10.65.13.94}"
node_iface="${K3S_FLANNEL_IFACE:-wlp4s0}"

if [[ -n ${K3S_TOKEN_FILE:-} ]]; then
  if [[ ! -r ${K3S_TOKEN_FILE} ]]; then
    echo "K3s token file is not readable: $K3S_TOKEN_FILE" >&2
    exit 1
  fi
  K3S_TOKEN="$(<"$K3S_TOKEN_FILE")"
fi

if [[ -z ${K3S_TOKEN:-} ]]; then
  read -r -s -p "K3s join token: " K3S_TOKEN
  echo
fi
if [[ -z ${K3S_TOKEN} ]]; then
  echo "K3s token cannot be empty" >&2
  exit 1
fi

"$(dirname "${BASH_SOURCE[0]}")/configure-ubuntu-network.sh"

install -d -m 0755 /etc/rancher/k3s
cat > /etc/rancher/k3s/resolv.conf <<'EOF'
nameserver 10.64.0.135
nameserver 10.64.0.134
options timeout:2 attempts:2
EOF

curl -sfL https://get.k3s.io | \
  K3S_URL="$server_url" K3S_TOKEN="$K3S_TOKEN" \
  INSTALL_K3S_EXEC="agent --node-ip $node_ip --flannel-iface $node_iface --resolv-conf /etc/rancher/k3s/resolv.conf --node-label sapiece.io/gateway=true" sh -

systemctl enable --now k3s-agent
systemctl --no-pager --full status k3s-agent
