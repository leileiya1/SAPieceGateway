#!/usr/bin/env bash
set -euo pipefail

archive="${1:?Usage: sudo $0 /path/to/sapiece-gateway-image.tar}"
if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo" >&2
  exit 1
fi
k3s ctr images import "$archive"
k3s ctr images list | grep 'sapiece-gateway:native-k3s'
