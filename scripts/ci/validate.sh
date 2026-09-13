#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
retry() {
  local attempt
  for attempt in 1 2 3; do
    "$@" && return 0
    [[ "$attempt" == 3 ]] && return 1
    sleep $((attempt * 5))
  done
}

mvn -s .mvn/settings.xml -B -ntp -Pnative clean test
retry docker pull golang:1.27-bookworm
retry docker pull bufbuild/buf:1.73.0
docker run --rm -v "$repo_root/examples/grpc-echo:/workspace" -w /workspace \
  -e CGO_ENABLED=1 golang:1.27-bookworm \
  sh -ec 'go vet ./... && go test -race ./...'
docker run --rm -v "$repo_root/examples/grpc-echo:/workspace" -w /workspace \
  bufbuild/buf:1.73.0 lint
sudo /usr/local/bin/k3s kubectl kustomize k8s > dist-k8s.yaml
sudo /usr/local/bin/k3s kubectl apply --dry-run=client --validate=false \
  -f dist-k8s.yaml >/dev/null
rm -f dist-k8s.yaml
