# HTTP to gRPC multi-instance demo

This example proves the complete request path:

```text
HTTP/JSON client -> SAPiece Gateway -> Envoy transcoder -> Go gRPC Pods
```

`proto/echo.proto` is the public contract. `internal/schema` builds the same
descriptor for the dynamic Go service and the Envoy descriptor image, so the
two artifacts cannot silently use different field or method definitions.

## Build and deploy

The reproducible multi-stage build uses the pinned Go and Envoy images:

```bash
docker build --target server -t sapiece-grpc-echo:v2 .
docker build --target transcoder -t sapiece-grpc-transcoder:v2 .
```

For an offline x86_64 deployment, cross-compile `dist/grpc-echo` and generate
`dist/echo.pb`, then use `Dockerfile.prebuilt`. Import both images into K3s and
apply `k8s/grpc-demo.yaml` from the repository root.

The manifest runs three gRPC Pods and two Envoy Pods on `school-linux`. Envoy
uses the headless gRPC Service for per-Pod DNS discovery, gRPC health checks,
one-second DNS refresh, and round-robin balancing. The Go process reports
`NOT_SERVING` and drains for four seconds before exit. Connection retries are
enabled only for the idempotent `Echo` method.

The integration test creates an authenticated, temporary Gateway route:

```text
POST /<test-prefix>_grpc/Echo
  -> /sapiece.demo.v1.EchoService/Echo
  -> grpc-transcoder.sapiece.svc.cluster.local:8080
  -> grpc-echo-headless.sapiece.svc.cluster.local:9090
```

Run it on `school-linux`:

```bash
KUBECTL='sudo k3s kubectl' \
GATEWAY_BASE_URL=http://127.0.0.1:30096 \
python3 src/test/integration/test_http_grpc_transcoding.py
```

## Production gaps exposed by the test

- The demo uses plaintext h2c inside the cluster. Add workload identity and
  mTLS before traffic crosses an untrusted node or network.
- HTTP identity headers and the Gateway HMAC signature are not validated by
  the demo service. Define a small, versioned metadata contract and enforce it
  in server interceptors.
- Only unary RPCs were tested. Client/server streaming needs separate limits,
  cancellation, backpressure, and long-lived connection tests.
- Timeouts, request size, and retry policy must be configured per RPC. Retrying
  mutations globally can execute a write more than once.
- Envoy currently has local access logs and admin stats only. Production needs
  trace-context propagation, Prometheus scraping, dashboards, and alerts.
- All five demo Pods intentionally run on `school-linux`. This verifies process
  redundancy but not node redundancy; production replicas must span nodes.
