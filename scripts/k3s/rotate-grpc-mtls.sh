#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

namespace="${NAMESPACE:-sapiece}"
valid_days="${VALID_DAYS:-365}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
chmod 700 "$work_dir"

openssl ecparam -name prime256v1 -genkey -noout -out "$work_dir/ca.key"
openssl req -x509 -new -sha256 -key "$work_dir/ca.key" -days 3650 \
  -subj "/CN=SAPiece internal gRPC CA" -out "$work_dir/ca.crt"

cat > "$work_dir/server.ext" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:grpc-echo,DNS:grpc-echo-headless,DNS:grpc-echo-headless.sapiece.svc,DNS:grpc-echo-headless.sapiece.svc.cluster.local
EOF
openssl ecparam -name prime256v1 -genkey -noout -out "$work_dir/server.key"
openssl req -new -sha256 -key "$work_dir/server.key" \
  -subj "/CN=grpc-echo-headless.sapiece.svc.cluster.local" -out "$work_dir/server.csr"
openssl x509 -req -sha256 -in "$work_dir/server.csr" -CA "$work_dir/ca.crt" \
  -CAkey "$work_dir/ca.key" -CAcreateserial -days "$valid_days" \
  -extfile "$work_dir/server.ext" -out "$work_dir/server.crt"

cat > "$work_dir/client.ext" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth
subjectAltName=URI:spiffe://sapiece/grpc-transcoder
EOF
openssl ecparam -name prime256v1 -genkey -noout -out "$work_dir/client.key"
openssl req -new -sha256 -key "$work_dir/client.key" \
  -subj "/CN=grpc-transcoder" -out "$work_dir/client.csr"
openssl x509 -req -sha256 -in "$work_dir/client.csr" -CA "$work_dir/ca.crt" \
  -CAkey "$work_dir/ca.key" -CAcreateserial -days "$valid_days" \
  -extfile "$work_dir/client.ext" -out "$work_dir/client.crt"

k3s kubectl -n "$namespace" create secret generic grpc-echo-server-tls \
  --from-file=ca.crt="$work_dir/ca.crt" \
  --from-file=tls.crt="$work_dir/server.crt" \
  --from-file=tls.key="$work_dir/server.key" \
  --dry-run=client -o yaml | k3s kubectl apply -f -
k3s kubectl -n "$namespace" create secret generic grpc-transcoder-client-tls \
  --from-file=ca.crt="$work_dir/ca.crt" \
  --from-file=tls.crt="$work_dir/client.crt" \
  --from-file=tls.key="$work_dir/client.key" \
  --dry-run=client -o yaml | k3s kubectl apply -f -

openssl verify -CAfile "$work_dir/ca.crt" "$work_dir/server.crt" "$work_dir/client.crt"
openssl x509 -in "$work_dir/server.crt" -noout -subject -dates
openssl x509 -in "$work_dir/client.crt" -noout -subject -dates

if k3s kubectl -n "$namespace" get deployment grpc-echo grpc-transcoder >/dev/null 2>&1; then
  k3s kubectl -n "$namespace" rollout restart deployment/grpc-echo deployment/grpc-transcoder
fi
