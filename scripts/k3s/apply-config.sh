#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
k3s kubectl apply -f "$repo_root/k8s/configmap.yaml"
k3s kubectl -n sapiece rollout restart deployment/sapiece-gateway
k3s kubectl -n sapiece rollout status deployment/sapiece-gateway --timeout=240s
