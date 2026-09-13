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

# Run Java tests in the same pinned GraalVM/JDK image used by native builds.
# This avoids stale repository IDs and JDK drift in the long-lived agent home.
mkdir -p "$repo_root/target/surefire-reports"
retry docker build --pull -f Dockerfile.k3s --target test-reports \
  --output "type=local,dest=$repo_root/target/surefire-reports" .
retry docker pull golang:1.27-bookworm
retry docker pull bufbuild/buf:1.73.0
retry docker run --rm \
  -v "$repo_root/examples/grpc-echo:/workspace" \
  -v sapiece-jenkins-go-mod:/go/pkg/mod \
  -v sapiece-jenkins-go-build:/root/.cache/go-build \
  -w /workspace \
  -e CGO_ENABLED=1 \
  -e 'GOPROXY=https://goproxy.cn|https://proxy.golang.org|direct' \
  golang:1.27-bookworm \
  sh -ec 'go mod download && go vet ./... && go test -race ./...'
retry docker run --rm -v "$repo_root/examples/grpc-echo:/workspace" -w /workspace \
  bufbuild/buf:1.73.0 lint
sudo /usr/local/bin/k3s kubectl kustomize k8s > dist-k8s.yaml
sudo /usr/local/bin/k3s kubectl apply --dry-run=client --validate=false \
  -f dist-k8s.yaml >/dev/null
rm -f dist-k8s.yaml
