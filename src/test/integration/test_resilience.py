#!/usr/bin/env python3
"""Opt-in concurrency, multi-instance, and middleware fault tests."""
from __future__ import annotations

import concurrent.futures
import json
import os
import subprocess
import time
import urllib.error
import urllib.request

import test_all_endpoints as api

SECOND = os.getenv("SECOND_GATEWAY_CONTAINER", "sapiece-gateway-native-e2e")
SECOND_BASE = os.getenv("SECOND_GATEWAY_BASE_URL", "http://127.0.0.1:8097")
NETWORK = os.getenv("GATEWAY_NETWORK", "sapiece-gateway-middleware")
IMAGE = os.getenv("GATEWAY_IMAGE", "sapiece-gateway:native")
MYSQL = os.getenv("MYSQL_CONTAINER", "sapiece-gateway-mysql")
REDIS = os.getenv("REDIS_CONTAINER", "sapiece-gateway-redis")


def docker(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["docker", *args], text=True, capture_output=True, check=check)


def health(base: str) -> tuple[int, dict]:
    try:
        with urllib.request.urlopen(base + "/actuator/health/readiness", timeout=5) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b"{}")
    except (urllib.error.URLError, TimeoutError):
        return 0, {}


def wait_health(base: str, expected_up: bool, timeout: int = 45) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        status, body = health(base)
        is_up = status == 200 and body.get("status") == "UP"
        if is_up == expected_up:
            return
        time.sleep(1)
    raise AssertionError(f"health expectation not reached: base={base}, expected_up={expected_up}")


def start_second_gateway() -> None:
    docker("rm", "-f", SECOND, check=False)
    docker("run", "-d", "--name", SECOND, "--network", NETWORK,
           "-p", "127.0.0.1:8097:8080", "--env-file", ".env",
           "-e", "SPRING_PROFILES_ACTIVE=prod", "-e", "SERVER_PORT=8080",
           "-e", "DB_URL=r2dbc:mysql://mysql:3306/sapiece_gateway",
           "-e", "DB_USERNAME=gateway_native", "-e", "REDIS_HOST=redis",
           "-e", "KUBERNETES_DNS_DISCOVERY_ENABLED=false",
           IMAGE, "-Xmx512m")
    wait_health(SECOND_BASE, True, 60)


def run() -> None:
    if os.getenv("ALLOW_FAULT_TESTS") != "1":
        raise SystemExit("Set ALLOW_FAULT_TESTS=1 to run the dedicated-stack fault tests")
    api.seed()
    paused: set[str] = set()
    try:
        login = api.ok("resilience admin login", api.request(
            "POST", "/auth/login", body={"userName": api.ADMIN_NAME, "password": api.ADMIN_PASSWORD}))
        token = login["data"]["accessToken"]
        api.token_values.extend([token, login["data"]["refreshToken"]])

        idem = api.ok("concurrency token", api.request("GET", "/test/idempotent/token", token=token))
        idem_token = idem["data"]["token"]
        api.fixture["idem_token"] = idem_token
        body = {"orderId": api.PREFIX, "amount": 1.0, "productName": "race"}
        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
            futures = [pool.submit(api.request, "POST", "/test/idempotent/submit",
                                   body=body, token=token, headers={"Idempotent-Token": idem_token})
                       for _ in range(16)]
        responses = [future.result() for future in futures]
        successes = [item for item in responses if item[0] == 200 and item[1].get("code") == 200]
        assert len(successes) == 1, responses
        assert all(item[0] in (200, 409, 429) for item in responses), responses
        print("PASS idempotency is atomic under 16 concurrent requests", flush=True)

        start_second_gateway()
        shared_ip = "203.0.113.211"
        api.ok("write shared Redis list", api.request("POST", "/admin/ip/blacklist",
               body={"ip": shared_ip}, token=token))
        first_base = api.BASE
        api.BASE = SECOND_BASE
        listed = api.ok("read shared Redis list from second gateway",
                        api.request("GET", "/admin/ip/blacklist", token=token))
        api.BASE = first_base
        assert shared_ip in listed["data"]
        api.ok("remove shared Redis list entry", api.request("DELETE", "/admin/ip/blacklist",
               body={"ip": shared_ip}, token=token))

        docker("pause", REDIS); paused.add(REDIS)
        wait_health(api.BASE, False)
        print("PASS readiness reports Redis outage", flush=True)
        docker("unpause", REDIS); paused.remove(REDIS)
        wait_health(api.BASE, True)

        docker("pause", MYSQL); paused.add(MYSQL)
        wait_health(api.BASE, False)
        print("PASS readiness reports MySQL outage", flush=True)
        docker("unpause", MYSQL); paused.remove(MYSQL)
        wait_health(api.BASE, True)
        print("PASS middleware recovery", flush=True)
    finally:
        api.BASE = os.getenv("GATEWAY_BASE_URL", "http://127.0.0.1:8096").rstrip("/")
        for container in list(paused):
            docker("unpause", container, check=False)
        docker("rm", "-f", SECOND, check=False)
        api.cleanup()


if __name__ == "__main__":
    run()
