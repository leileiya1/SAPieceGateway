#!/usr/bin/env python3
"""End-to-end HTTP/JSON -> Gateway -> Envoy -> multi-instance gRPC test."""
from __future__ import annotations

import concurrent.futures
import json
import subprocess
import time
import urllib.error
import urllib.request

import test_all_endpoints as api

KUBECTL = api.KUBECTL
NAMESPACE = api.K8S_NAMESPACE
PATH_PREFIX = "/" + api.PREFIX + "_grpc"


def kubectl(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    if not KUBECTL:
        raise RuntimeError("Set KUBECTL, for example: KUBECTL='sudo k3s kubectl'")
    return subprocess.run(KUBECTL + ["-n", NAMESPACE, *args], text=True,
                          capture_output=True, check=check)


def call(method: str, body: dict, token: str, timeout: int = 10) -> tuple[int, dict]:
    request = urllib.request.Request(
        api.BASE + PATH_PREFIX + "/" + method,
        data=json.dumps(body).encode(),
        headers={"Authorization": "Bearer " + token,
                 "Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            return error.code, {"raw": raw.decode(errors="replace")}


def wait_for_route(token: str, timeout: int = 60) -> None:
    deadline = time.time() + timeout
    last = (0, {})
    consecutive = 0
    while time.time() < deadline:
        last = call("Echo", {"message": "ready"}, token)
        if last[0] == 200 and last[1].get("message") == "ready":
            consecutive += 1
            if consecutive >= 8:
                return
        else:
            consecutive = 0
        time.sleep(1)
    raise AssertionError(f"HTTP to gRPC route did not become ready: {last}")


def assert_error(label: str, response: tuple[int, dict], expected: set[int]) -> None:
    if response[0] not in expected:
        raise AssertionError(f"{label}: status={response[0]}, body={response[1]}")
    print("PASS", label, flush=True)


def run() -> None:
    api.seed()
    token = ""
    route_id = api.PREFIX + "_grpc_route"
    route_created = False
    try:
        kubectl("rollout", "status", "deployment/grpc-echo", "--timeout=180s")
        kubectl("rollout", "status", "deployment/grpc-transcoder", "--timeout=180s")
        login = api.ok("gRPC demo admin login", api.request(
            "POST", "/auth/login", body={"userName": api.ADMIN_NAME, "password": api.ADMIN_PASSWORD}))
        token = login["data"]["accessToken"]
        api.token_values.extend([token, login["data"]["refreshToken"]])

        route = api.route_payload(route_id)
        route.update({
            "uri": "lb://grpc-transcoder:8080",
            "requireAuth": 1,
            "predicates": json.dumps([
                {"name": "Path", "args": {"pattern": PATH_PREFIX + "/**"}},
            ]),
            "filters": json.dumps([
                {"name": "RewritePath", "args": {
                    "regexp": PATH_PREFIX + "/(?<method>.*)",
                    "replacement": "/sapiece.demo.v1.EchoService/${method}",
                }},
            ]),
        })
        api.ok("create HTTP to gRPC route", api.request(
            "POST", "/admin/route", body=route, token=token))
        route_created = True
        wait_for_route(token)

        status, response = call("Echo", {"message": "grpc", "repeat": 2}, token)
        if status != 200 or response.get("message") != "grpcgrpc" or not response.get("instance"):
            raise AssertionError(f"transcoded response mismatch: status={status}, body={response}")
        print("PASS HTTP JSON was transcoded to gRPC", flush=True)

        def one(index: int) -> tuple[int, dict]:
            return call("Echo", {"message": f"c{index}"}, token)

        with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
            responses = list(executor.map(one, range(80)))
        failed = [response for response in responses if response[0] != 200]
        instances = {response[1].get("instance") for response in responses if response[0] == 200}
        if failed:
            raise AssertionError(f"concurrent failures={len(failed)}, first={failed[0]}")
        if len(instances) < 2:
            raise AssertionError(f"requests reached only one gRPC instance: {instances}")
        print(f"PASS 80 concurrent requests across {len(instances)} gRPC instances", flush=True)

        status, stream = call("StreamEcho", {"message": "stream", "repeat": 4}, token)
        if status != 200 or not isinstance(stream, list) or len(stream) != 4:
            raise AssertionError(f"server stream mismatch: status={status}, body={stream}")
        if [item.get("message") for item in stream] != ["stream-1", "stream-2", "stream-3", "stream-4"]:
            raise AssertionError(f"server stream ordering mismatch: {stream}")
        print("PASS server-streaming gRPC was transcoded to ordered JSON", flush=True)

        assert_error("blank protobuf field validation", call("Echo", {}, token), {400})
        assert_error("gRPC InvalidArgument maps to HTTP 400",
                     call("Fail", {"mode": "invalid"}, token), {400})
        assert_error("gRPC Unavailable maps to HTTP 503",
                     call("Fail", {"mode": "unavailable"}, token), {503})
        assert_error("Envoy timeout is bounded",
                     call("Echo", {"message": "slow", "delay_ms": 3000}, token), {504})
        assert_error("unknown gRPC method is rejected",
                     call("Missing", {"message": "x"}, token), {404})

        pod = kubectl("get", "pod", "-l", "app=grpc-echo",
                      "-o", "jsonpath={.items[0].metadata.name}").stdout
        kubectl("delete", "pod", pod, "--wait=false")
        failures = []
        for index in range(40):
            response = one(1000 + index)
            if response[0] != 200:
                failures.append(response)
            time.sleep(0.1)
        if failures:
            raise AssertionError(f"pod replacement caused {len(failures)} request failures; first={failures[0]}")
        kubectl("rollout", "status", "deployment/grpc-echo", "--timeout=180s")
        print("PASS gRPC Pod replacement caused no HTTP request failures", flush=True)

        transcoder_pod = kubectl("get", "pod", "-l", "app=grpc-transcoder",
                                 "-o", "jsonpath={.items[0].metadata.name}").stdout
        kubectl("delete", "pod", transcoder_pod, "--wait=false")
        failures = []
        for index in range(40):
            response = one(2000 + index)
            if response[0] != 200:
                failures.append(response)
            time.sleep(0.1)
        if failures:
            raise AssertionError(
                f"transcoder replacement caused {len(failures)} request failures; first={failures[0]}")
        kubectl("rollout", "status", "deployment/grpc-transcoder", "--timeout=180s")
        print("PASS Envoy replacement caused no HTTP request failures", flush=True)
    finally:
        if route_created and token:
            api.request("DELETE", "/admin/route/routeId/" + route_id, token=token)
        api.cleanup()


if __name__ == "__main__":
    run()
