# Native gateway integration test

`test_all_endpoints.py` runs on the deployment host against the native container.
It creates random, isolated rows in the dedicated `sapiece_gateway` database,
tests every `/admin`, `/auth`, `/test`, and `/user` operation in OpenAPI, then
removes all fixture rows and related Redis keys.

```bash
python3 src/test/integration/test_all_endpoints.py
```

Requirements: `python3-bcrypt` and a healthy gateway. Docker mode uses the
`mysql` and `redis` containers. K3s mode sets `KUBECTL`, `K8S_NAMESPACE`, and a
NodePort/port-forward `GATEWAY_BASE_URL`; the suite then executes cleanup inside
the MySQL and Redis StatefulSets. A non-loopback target requires
`ALLOW_REMOTE_TEST=1`.

`test_resilience.py` adds a 16-request idempotency race, starts a second Native
gateway against the same MySQL/Redis network, verifies cross-instance IP
list visibility, and checks readiness/recovery while the dedicated Redis and
MySQL containers are paused. It is intentionally opt-in:

```bash
ALLOW_FAULT_TESTS=1 python3 src/test/integration/test_resilience.py
```

`test_k3s_lb.py` creates a temporary two-Pod Kubernetes Deployment and Service,
adds an `lb://service-name` database route, verifies proxying, deletes one Pod,
and verifies that the Service keeps the route available during replacement.

```bash
KUBECTL='sudo k3s kubectl' GATEWAY_BASE_URL=http://127.0.0.1:30096 \
  python3 src/test/integration/test_k3s_lb.py
```

`test_http_grpc_transcoding.py` exercises the persistent demo resources in
`k8s/grpc-demo.yaml`: Spring Gateway rewrites an authenticated HTTP/JSON route,
Envoy transcodes it to gRPC, and three Go instances run on `school-linux`. The
suite verifies concurrency, instance distribution, gRPC-to-HTTP errors, timeout
bounds, unknown methods, and zero-error Pod replacement.

```bash
KUBECTL='sudo k3s kubectl' GATEWAY_BASE_URL=http://127.0.0.1:30096 \
  python3 src/test/integration/test_http_grpc_transcoding.py
```
