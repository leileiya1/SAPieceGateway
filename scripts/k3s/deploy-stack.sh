#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
namespace="${NAMESPACE:-sapiece}"
kubectl=(k3s kubectl)

for secret in sapiece-gateway-secret grpc-echo-server-tls grpc-transcoder-client-tls; do
  "${kubectl[@]}" -n "$namespace" get secret "$secret" >/dev/null || {
    echo "Required secret is missing: $namespace/$secret" >&2
    exit 1
  }
done

not_ready="$("${kubectl[@]}" get nodes --no-headers | awk '$2 != "Ready" {print $1}')"
if [[ -n "$not_ready" ]]; then
  echo "K3s has non-ready nodes: $not_ready" >&2
  exit 1
fi

rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT
"${kubectl[@]}" kustomize "$repo_root/k8s" > "$rendered"
"${kubectl[@]}" apply --dry-run=client --validate=false -f "$rendered" >/dev/null
"${kubectl[@]}" apply -f "$rendered"

config_checksum="$(sha256sum "$repo_root/k8s/configmap.yaml" | cut -d' ' -f1)"
"${kubectl[@]}" -n "$namespace" annotate deployment/sapiece-gateway \
  sapiece.io/config-checksum="$config_checksum" --overwrite

"${kubectl[@]}" -n "$namespace" rollout status deployment/sapiece-gateway --timeout=300s
"${kubectl[@]}" -n "$namespace" rollout status deployment/grpc-echo --timeout=180s
"${kubectl[@]}" -n "$namespace" rollout status deployment/grpc-transcoder --timeout=180s
"${kubectl[@]}" -n observability rollout status deployment/prometheus --timeout=180s
"${kubectl[@]}" -n "$namespace" get pods -o wide
