#!/usr/bin/env python3
"""End-to-end Kubernetes Service DNS plus Gateway lb:// failover test."""
from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request

import test_all_endpoints as api

KUBECTL = api.KUBECTL
NAMESPACE = api.K8S_NAMESPACE
SERVICE = "lb-e2e-" + api.PREFIX.replace("_", "-")
PATH_PREFIX = "/" + api.PREFIX + "_lb"


def kubectl(*args: str, stdin: str | None = None, check: bool = True) -> subprocess.CompletedProcess:
    if not KUBECTL:
        raise RuntimeError("Set KUBECTL, for example: KUBECTL='sudo k3s kubectl'")
    return subprocess.run(KUBECTL + ["-n", NAMESPACE, *args], input=stdin, text=True,
                          capture_output=True, check=check)


def raw_gateway(path: str, token: str) -> tuple[int, str]:
    request = urllib.request.Request(api.BASE + path, headers={"Authorization": "Bearer " + token})
    try:
        # Route refresh and kube-proxy convergence are asynchronous across
        # gateway replicas. Keep each probe bounded so one stale connection
        # cannot consume the entire readiness window.
        with urllib.request.urlopen(request, timeout=3) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(errors="replace")
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        return 0, f"{type(error).__name__}: {error}"


def backend_manifest() -> str:
    return rf"""
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {SERVICE}
spec:
  replicas: 2
  selector:
    matchLabels: {{app: {SERVICE}}}
  template:
    metadata:
      labels: {{app: {SERVICE}}}
    spec:
      containers:
        - name: backend
          image: redis:7.4-alpine
          imagePullPolicy: IfNotPresent
          command: ["sh", "-c"]
          args:
            - |
              cat >/tmp/respond.sh <<'EOF'
              #!/bin/sh
              while IFS= read -r line; do
                [ "$line" = "$(printf '\r')" ] && break
              done
              printf 'HTTP/1.1 200 OK\r\nContent-Length: 9\r\nConnection: close\r\n\r\nk3s-lb-ok'
              EOF
              chmod +x /tmp/respond.sh
              exec nc -lk -p 8080 -e /tmp/respond.sh
          ports:
            - {{name: http, containerPort: 8080}}
          readinessProbe:
            httpGet: {{path: {PATH_PREFIX}/hello, port: http}}
            periodSeconds: 2
---
apiVersion: v1
kind: Service
metadata:
  name: {SERVICE}
spec:
  selector: {{app: {SERVICE}}}
  ports:
    - {{name: http, port: 80, targetPort: http}}
"""


def wait_for_proxy(admin_token: str, timeout: int = 60) -> None:
    deadline = time.time() + timeout
    last = (0, "")
    while time.time() < deadline:
        last = raw_gateway(PATH_PREFIX + "/hello", admin_token)
        if last == (200, "k3s-lb-ok"):
            return
        time.sleep(1)
    raise AssertionError(f"Kubernetes lb:// route did not become ready: {last}")


def run() -> None:
    api.seed()
    created = False
    route_created = False
    admin_token = ""
    route_id = api.PREFIX + "_lb_route"
    try:
        login = api.ok("K3s LB admin login", api.request(
            "POST", "/auth/login", body={"userName": api.ADMIN_NAME, "password": api.ADMIN_PASSWORD}))
        admin_token = login["data"]["accessToken"]
        api.token_values.extend([admin_token, login["data"]["refreshToken"]])

        kubectl("apply", "-f", "-", stdin=backend_manifest())
        created = True
        kubectl("rollout", "status", "deployment/" + SERVICE, "--timeout=120s")

        route = api.route_payload(route_id)
        route.update({"uri": "lb://" + SERVICE, "requireAuth": 1,
                      "predicates": json.dumps([{"name": "Path", "args": {"pattern": PATH_PREFIX + "/**"}}])})
        api.ok("create Kubernetes lb:// route", api.request(
            "POST", "/admin/route", body=route, token=admin_token))
        route_created = True
        wait_for_proxy(admin_token)

        pod = kubectl("get", "pod", "-l", "app=" + SERVICE,
                      "-o", "jsonpath={.items[0].metadata.name}").stdout
        kubectl("delete", "pod", pod, "--wait=false")
        wait_for_proxy(admin_token)
        print("PASS Kubernetes Service kept lb:// route available during pod replacement", flush=True)
    finally:
        if route_created and admin_token:
            api.request("DELETE", "/admin/route/routeId/" + route_id, token=admin_token)
        if created:
            kubectl("delete", "deployment,service", SERVICE, "--ignore-not-found=true", check=False)
        api.cleanup()


if __name__ == "__main__":
    run()
