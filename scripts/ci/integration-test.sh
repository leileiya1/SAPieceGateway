#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$repo_root/dist"
cd "$repo_root"
export KUBECTL='sudo /usr/local/bin/k3s kubectl'
export GATEWAY_BASE_URL='http://127.0.0.1:30096'
export ALLOW_REMOTE_TEST=1
{
  python3 src/test/integration/test_all_endpoints.py
  python3 src/test/integration/test_k3s_lb.py
  python3 src/test/integration/test_http_grpc_transcoding.py
} 2>&1 | tee "$repo_root/dist/integration.log"
