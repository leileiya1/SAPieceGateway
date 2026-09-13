#!/usr/bin/env python3
"""Black-box API robustness suite for the native gateway.

Run on the deployment host. The suite creates isolated MySQL fixtures with a
random prefix, exercises every controller operation advertised by OpenAPI, and
removes its MySQL/Redis data in a finally block. It never prints passwords or
bearer tokens.
"""
from __future__ import annotations

import hashlib
import json
import os
import secrets
import shlex
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import bcrypt

BASE = os.getenv("GATEWAY_BASE_URL", "http://127.0.0.1:8096").rstrip("/")
MYSQL_CONTAINER = os.getenv("MYSQL_CONTAINER", "mysql")
REDIS_CONTAINER = os.getenv("REDIS_CONTAINER", "redis")
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "")
KUBECTL = shlex.split(os.getenv("KUBECTL", ""))
K8S_NAMESPACE = os.getenv("K8S_NAMESPACE", "sapiece")
DATABASE = os.getenv("GATEWAY_DATABASE", "sapiece_gateway")
PREFIX = "e2e_" + secrets.token_hex(5)
ADMIN_NAME = PREFIX + "_admin"
USER_NAME = PREFIX + "_user"
ADMIN_PASSWORD = secrets.token_urlsafe(24)
USER_PASSWORD = secrets.token_urlsafe(24)
NEW_USER_PASSWORD = secrets.token_urlsafe(26)
PROVIDER = (PREFIX + "_oauth").upper()
ROUTE_ID = PREFIX + "_route"
GRAY_CODE = PREFIX + "_gray"

covered: set[tuple[str, str]] = set()
token_values: list[str] = []
fixture: dict[str, int | bool | str] = {}


def sql(statement: str) -> str:
    if KUBECTL:
        command = KUBECTL + ["-n", K8S_NAMESPACE, "exec", "-i", "statefulset/mysql", "--",
                             "sh", "-c", f'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N {DATABASE}']
    else:
        command = ["docker", "exec", "-i", MYSQL_CONTAINER, "sh", "-c",
                   f'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N {DATABASE}']
    proc = subprocess.run(
        command,
        input=statement, text=True, capture_output=True, check=False,
    )
    if proc.returncode:
        raise RuntimeError("MySQL command failed: " + proc.stderr.strip())
    return proc.stdout.strip()


def redis_del(*keys: str) -> None:
    if not keys:
        return
    if KUBECTL:
        command = KUBECTL + ["-n", K8S_NAMESPACE, "exec", "statefulset/redis", "--",
                             "sh", "-c", 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning DEL "$@"',
                             "redis-del", *keys]
    else:
        command = ["docker", "exec"]
        if REDIS_PASSWORD:
            command += ["-e", "REDISCLI_AUTH=" + REDIS_PASSWORD]
        command += [REDIS_CONTAINER, "redis-cli", "DEL", *keys]
    subprocess.run(command,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


def redis_scan_delete(pattern: str) -> None:
    if KUBECTL:
        command = KUBECTL + ["-n", K8S_NAMESPACE, "exec", "statefulset/redis", "--",
                             "sh", "-c", 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --raw --scan --pattern "$1"',
                             "redis-scan", pattern]
    else:
        command = ["docker", "exec"]
        if REDIS_PASSWORD:
            command += ["-e", "REDISCLI_AUTH=" + REDIS_PASSWORD]
        command += [REDIS_CONTAINER, "redis-cli", "--raw", "--scan", "--pattern", pattern]
    found = subprocess.run(
        command,
        text=True, capture_output=True, check=False,
    )
    keys = [line for line in found.stdout.splitlines() if line]
    redis_del(*keys)


def request(method: str, path: str, *, template: str | None = None, body=None,
            token: str | None = None, headers: dict[str, str] | None = None,
            timeout: int = 20) -> tuple[int, dict]:
    if template:
        covered.add((method.lower(), template))
    request_headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        request_headers["Authorization"] = "Bearer " + token
    if headers:
        request_headers.update(headers)
    data = json.dumps(body, separators=(",", ":")).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            payload = {"raw": raw.decode(errors="replace")}
        return error.code, payload
    except urllib.error.URLError as error:
        raise AssertionError(f"{method} {path} network error: {error.reason}") from error


def ok(label: str, response: tuple[int, dict]) -> dict:
    status, payload = response
    if status != 200 or payload.get("code") != 200:
        raise AssertionError(f"{label}: expected success, got HTTP {status}, body={payload}")
    print("PASS", label, flush=True)
    return payload


def rejected(label: str, response: tuple[int, dict], *, allowed_server_statuses: set[int] | None = None) -> dict:
    status, payload = response
    allowed_server_statuses = allowed_server_statuses or set()
    if (status >= 500 and status not in allowed_server_statuses) or (status == 200 and payload.get("code") == 200):
        raise AssertionError(f"{label}: expected controlled rejection, got HTTP {status}, body={payload}")
    print("PASS", label, flush=True)
    return payload


def route_payload(route_id: str) -> dict:
    return {
        "routeId": route_id, "routeName": "integration fixture",
        "uri": "http://127.0.0.1:9",
        "predicates": json.dumps([{"name": "Path", "args": {"pattern": f"/{route_id}/**"}}]),
        "filters": "[]", "metadata": "{}", "orderNum": 1000,
        "requireAuth": 1, "permissionLogic": "OR", "rateLimitEnabled": 0,
        "rateLimitQps": 10, "rateLimitStrategy": "ip", "cacheEnabled": 0,
        "cacheTtl": 60, "retryEnabled": 0, "retryTimes": 0,
        "timeoutMs": 1000, "status": 1, "description": "temporary integration route",
    }


def seed() -> None:
    admin_hash = bcrypt.hashpw(ADMIN_PASSWORD.encode(), bcrypt.gensalt()).decode()
    user_hash = bcrypt.hashpw(USER_PASSWORD.encode(), bcrypt.gensalt()).decode()
    role_row = sql("SELECT id FROM sys_role WHERE role_code='ADMIN' LIMIT 1;")
    fixture["role_created"] = not bool(role_row)
    if role_row:
        role_id = int(role_row)
    else:
        role_id = int(sql(
            "INSERT INTO sys_role(role_code,role_name,status,del_flag,remark,creator) "
            f"VALUES('ADMIN','Integration admin',1,0,'{PREFIX}','{PREFIX}');"
            "SELECT LAST_INSERT_ID();"
        ))
    fixture["role_id"] = role_id

    sql(
        "START TRANSACTION;"
        "INSERT INTO sys_user(user_name,nick_name,password,status,del_flag,password_last_changed_at,remark,creator) VALUES"
        f"('{ADMIN_NAME}','Integration admin','{admin_hash}',1,0,UTC_TIMESTAMP() - INTERVAL 1 DAY,'{PREFIX}','{PREFIX}'),"
        f"('{USER_NAME}','Integration user','{user_hash}',1,0,UTC_TIMESTAMP() - INTERVAL 1 DAY,'{PREFIX}','{PREFIX}');"
        f"SET @admin_id=(SELECT id FROM sys_user WHERE user_name='{ADMIN_NAME}');"
        f"INSERT INTO sys_user_role(user_id,role_id,creator) VALUES(@admin_id,{role_id},'{PREFIX}');"
        "COMMIT;"
    )
    fixture["admin_id"] = int(sql(f"SELECT id FROM sys_user WHERE user_name='{ADMIN_NAME}';"))
    fixture["user_id"] = int(sql(f"SELECT id FROM sys_user WHERE user_name='{USER_NAME}';"))

    permissions = ["gateway:route:list", "gateway:route:query", "gateway:route:add",
                   "gateway:route:update", "gateway:route:delete", "gateway:route:refresh"]
    for index, permission in enumerate(permissions):
        menu_id = int(sql(
            "INSERT INTO sys_menu(parent_id,menu_name,menu_type,menu_sort,permission_code,status,del_flag,remark,creator) "
            f"VALUES(0,'{PREFIX}_{index}','F',{index},'{permission}',1,0,'{PREFIX}','{PREFIX}');"
            "SELECT LAST_INSERT_ID();"
        ))
        sql(f"INSERT INTO sys_role_menu(role_id,menu_id,creator) VALUES({role_id},{menu_id},'{PREFIX}');")

    fixture["route_id"] = int(sql("INSERT INTO sys_gateway_route(route_id,route_name,uri,predicates,filters,metadata,order_num,require_auth,"
        "permission_logic,rate_limit_enabled,rate_limit_qps,rate_limit_strategy,cache_enabled,cache_ttl,retry_enabled,"
        "retry_times,timeout_ms,status,description,creator) VALUES"
        f"('{ROUTE_ID}','fixture route','http://127.0.0.1:9','[{{\"name\":\"Path\",\"args\":{{\"pattern\":\"/{ROUTE_ID}/**\"}}}}]',"
        f"'[]','{{}}',1000,1,'OR',0,10,'ip',0,60,0,0,1000,1,'{PREFIX}','{PREFIX}');"
        "SELECT LAST_INSERT_ID();"))
    fixture["gray_id"] = int(sql(
        "INSERT INTO sys_gray_rule(rule_name,rule_code,service_id,target_uri,stable_uri,strategy_type,strategy_config,priority,status,description,creator) VALUES"
        f"('fixture gray','{GRAY_CODE}','{ROUTE_ID}','http://127.0.0.1:9','http://127.0.0.1:9','USER_ID','{{\"userIds\":[{fixture['user_id']}]}}',1000,1,'{PREFIX}','{PREFIX}');"
        "SELECT LAST_INSERT_ID();"
    ))
    fixture["oauth_user_id"] = int(sql(
        "INSERT INTO sys_oauth_config(provider,provider_name,client_id,client_secret,authorization_uri,token_uri,user_info_uri,redirect_uri,scopes,status,sort_order,creator) VALUES"
        f"('{PROVIDER}','Integration OAuth','dummy-client','dummy-secret','https://example.invalid/oauth/authorize','https://example.invalid/token','https://example.invalid/user','http://127.0.0.1/callback','profile',1,999,'{PREFIX}');"
        "INSERT INTO sys_oauth_user(provider,oauth_id,oauth_name,oauth_nickname,raw_user_info) VALUES"
        f"('{PROVIDER}','{PREFIX}','fixture','Fixture','{{}}');"
        "SELECT LAST_INSERT_ID();"
    ))
    sql("INSERT INTO sys_audit_log(trace_id,user_id,user_name,module,operation,description,status,response_code,client_ip,operate_time) VALUES"
        f"('{PREFIX}',{fixture['user_id']},'{USER_NAME}','AUTH','LOGIN','fixture success',1,200,'198.51.100.31',UTC_TIMESTAMP()),"
        f"('{PREFIX}',{fixture['user_id']},'{USER_NAME}','AUTH','LOGIN','fixture failure',0,401,'198.51.100.31',UTC_TIMESTAMP());")
    fixture["audit_id"] = int(sql(f"SELECT MIN(id) FROM sys_audit_log WHERE trace_id='{PREFIX}';"))
    print("FIXTURE MySQL test data created", flush=True)


def cleanup() -> None:
    try:
        sql(
            f"DELETE FROM sys_role_menu WHERE creator='{PREFIX}';"
            f"DELETE FROM sys_user_role WHERE creator='{PREFIX}';"
            f"DELETE FROM sys_menu WHERE creator='{PREFIX}';"
            f"DELETE FROM sys_gray_rule WHERE creator='{PREFIX}' OR rule_code LIKE '{PREFIX}%';"
            f"DELETE FROM sys_gateway_route WHERE creator='{PREFIX}' OR route_id LIKE '{PREFIX}%';"
            f"DELETE FROM sys_oauth_user WHERE oauth_id='{PREFIX}';"
            f"DELETE FROM sys_oauth_config WHERE creator='{PREFIX}';"
            f"DELETE FROM sys_audit_log WHERE trace_id='{PREFIX}' OR user_name IN ('{ADMIN_NAME}','{USER_NAME}');"
            f"DELETE FROM sys_user WHERE creator='{PREFIX}';"
        )
        if fixture.get("role_created"):
            sql(f"DELETE FROM sys_role WHERE id={fixture['role_id']} AND remark='{PREFIX}';")
    except Exception as error:
        print("CLEANUP WARNING", error, file=sys.stderr)
    ids = [fixture.get("admin_id"), fixture.get("user_id")]
    keys = []
    for user_id in ids:
        if user_id:
            keys += [f"user:roles:{user_id}", f"user:permissions:{user_id}",
                     f"user:pwdver:{user_id}", f"user:blacklist:{user_id}"]
    keys += ["token:blacklist:" + hashlib.md5(token.encode()).hexdigest() for token in token_values]
    if fixture.get("idem_token"):
        keys += [f"idempotent:token:order:create:{fixture['idem_token']}",
                 f"idempotent:order:create:{fixture['idem_token']}"]
    redis_del(*keys)
    redis_scan_delete(f"*{PREFIX}*")
    print("CLEANUP MySQL and Redis test data removed", flush=True)


def exercise() -> None:
    health_status, health = request("GET", "/health")
    assert health_status == 200 and health.get("status") == "UP", health

    rejected("blank login", request("POST", "/auth/login", template="/auth/login", body={}))
    admin_login = ok("admin login", request("POST", "/auth/login", body={"userName": ADMIN_NAME, "password": ADMIN_PASSWORD}))
    user_login = ok("normal login", request("POST", "/auth/login", body={"userName": USER_NAME, "password": USER_PASSWORD}))
    admin_access = admin_login["data"]["accessToken"]
    admin_refresh = admin_login["data"]["refreshToken"]
    user_access = user_login["data"]["accessToken"]
    token_values.extend([admin_access, admin_refresh, user_access, user_login["data"]["refreshToken"]])
    ok("token info", request("GET", "/auth/info", template="/auth/info", token=admin_access))
    rejected("legacy refresh rejects access token", request("POST", "/auth/refresh", template="/auth/refresh", body={}, token=admin_access))

    # Admin security and blacklists.
    rejected("normal user denied admin", request("GET", "/admin/ip/blacklist", token=user_access))
    ok("list IP blacklist", request("GET", "/admin/ip/blacklist", template="/admin/ip/blacklist", token=admin_access))
    rejected("reject malformed IP", request("POST", "/admin/ip/blacklist", body={"ip": "not an ip"}, token=admin_access))
    ok("add IP blacklist", request("POST", "/admin/ip/blacklist", template="/admin/ip/blacklist", body={"ip": "198.51.100.77"}, token=admin_access))
    ok("remove IP blacklist", request("DELETE", "/admin/ip/blacklist", template="/admin/ip/blacklist", body={"ip": "198.51.100.77"}, token=admin_access))
    ok("list IP whitelist", request("GET", "/admin/ip/whitelist", template="/admin/ip/whitelist", token=admin_access))
    ok("add IP whitelist", request("POST", "/admin/ip/whitelist", template="/admin/ip/whitelist", body={"ip": "203.0.113.77"}, token=admin_access))
    ok("remove IP whitelist", request("DELETE", "/admin/ip/whitelist", template="/admin/ip/whitelist", body={"ip": "203.0.113.77"}, token=admin_access))
    ok("blacklist token", request("POST", "/admin/token/blacklist", template="/admin/token/blacklist", body={"token": user_access, "durationHours": 1}, token=admin_access))
    check = ok("check token blacklist", request("POST", "/admin/token/blacklist/check", template="/admin/token/blacklist/check", body={"token": user_access}, token=admin_access))
    assert check["data"]["isBlacklisted"] is True
    ok("remove token blacklist", request("DELETE", "/admin/token/blacklist", template="/admin/token/blacklist", body={"token": user_access}, token=admin_access))
    ok("blacklist user", request("POST", "/admin/user/blacklist", template="/admin/user/blacklist", body={"userId": fixture["user_id"], "durationHours": 1}, token=admin_access))
    check = ok("check user blacklist", request("GET", f"/admin/user/blacklist/check/{fixture['user_id']}", template="/admin/user/blacklist/check/{userId}", token=admin_access))
    assert check["data"]["isBlacklisted"] is True
    redis_del(f"user:blacklist:{fixture['user_id']}")

    # Route CRUD and validation.
    ok("route list", request("GET", "/admin/route/list", template="/admin/route/list", token=admin_access))
    ok("enabled routes", request("GET", "/admin/route/enabled", template="/admin/route/enabled", token=admin_access))
    ok("route by id", request("GET", f"/admin/route/{fixture['route_id']}", template="/admin/route/{id}", token=admin_access))
    ok("route by route id", request("GET", f"/admin/route/routeId/{ROUTE_ID}", template="/admin/route/routeId/{routeId}", token=admin_access))
    ok("route search", request("GET", "/admin/route/search?keyword=" + PREFIX, template="/admin/route/search", token=admin_access))
    rejected("reject unsafe route URI", request("POST", "/admin/route", body={"routeId": PREFIX + "_bad", "uri": "file:///etc/passwd", "predicates": "[]"}, token=admin_access))
    route_one = PREFIX + "_created1"
    created = ok("create route", request("POST", "/admin/route", template="/admin/route", body=route_payload(route_one), token=admin_access))
    route_one_id = created["data"]["id"]
    ok("update route", request("PUT", f"/admin/route/{route_one_id}", template="/admin/route/{id}", body={"description": "updated", "timeoutMs": 1500}, token=admin_access))
    ok("disable route", request("PATCH", f"/admin/route/{route_one_id}/status?status=0", template="/admin/route/{id}/status", token=admin_access))
    rejected("reject invalid route status", request("PATCH", f"/admin/route/{route_one_id}/status?status=9", token=admin_access))
    ok("delete route by id", request("DELETE", f"/admin/route/{route_one_id}", template="/admin/route/{id}", token=admin_access))
    route_two = PREFIX + "_created2"
    ok("create second route", request("POST", "/admin/route", body=route_payload(route_two), token=admin_access))
    ok("delete route by route id", request("DELETE", f"/admin/route/routeId/{route_two}", template="/admin/route/routeId/{routeId}", token=admin_access))
    ok("refresh routes", request("POST", "/admin/route/refresh", template="/admin/route/refresh", body={}, token=admin_access))
    ok("route stats", request("GET", "/admin/route/stats", template="/admin/route/stats", token=admin_access))

    # Gray rules.
    ok("gray rules", request("GET", "/admin/gray/rules", template="/admin/gray/rules", token=admin_access))
    ok("gray rules by service", request("GET", f"/admin/gray/rules/service/{ROUTE_ID}", template="/admin/gray/rules/service/{serviceId}", token=admin_access))
    ok("gray rule by id", request("GET", f"/admin/gray/rules/{fixture['gray_id']}", template="/admin/gray/rules/{id}", token=admin_access))
    rejected("reject incomplete gray rule", request("POST", "/admin/gray/rules", body={}, token=admin_access))
    gray_body = {"ruleName": "created gray", "ruleCode": PREFIX + "_created_gray", "serviceId": ROUTE_ID,
                 "targetUri": "http://127.0.0.1:9", "stableUri": "http://127.0.0.1:9",
                 "strategyType": "USER_ID", "strategyConfig": json.dumps({"userIds": [fixture["user_id"]]}),
                 "priority": 999, "status": 1, "description": PREFIX}
    gray = ok("create gray rule", request("POST", "/admin/gray/rules", template="/admin/gray/rules", body=gray_body, token=admin_access))
    gray_id = gray["data"]["id"]
    ok("update gray rule", request("PUT", f"/admin/gray/rules/{gray_id}", template="/admin/gray/rules/{id}", body={"description": "updated"}, token=admin_access))
    ok("disable gray rule", request("POST", f"/admin/gray/rules/{gray_id}/disable", template="/admin/gray/rules/{id}/disable", body={}, token=admin_access))
    ok("enable gray rule", request("POST", f"/admin/gray/rules/{gray_id}/enable", template="/admin/gray/rules/{id}/enable", body={}, token=admin_access))
    ok("refresh gray rules", request("POST", "/admin/gray/rules/refresh", template="/admin/gray/rules/refresh", body={}, token=admin_access))
    ok("delete gray rule", request("DELETE", f"/admin/gray/rules/{gray_id}", template="/admin/gray/rules/{id}", token=admin_access))

    # Audit query variants; clean uses a 100-year cutoff so it cannot remove current data.
    ok("audit by id", request("GET", f"/admin/audit-log/{fixture['audit_id']}", template="/admin/audit-log/{id}", token=admin_access))
    ok("audit by user", request("GET", f"/admin/audit-log/user/{fixture['user_id']}", template="/admin/audit-log/user/{userId}", token=admin_access))
    now = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime())
    ok("audit time range", request("POST", "/admin/audit-log/time-range", template="/admin/audit-log/time-range", body={"startTime": "2020-01-01 00:00:00", "endTime": now}, token=admin_access))
    rejected("reject reversed audit range", request("POST", "/admin/audit-log/time-range", body={"startTime": now, "endTime": "2020-01-01 00:00:00"}, token=admin_access))
    search = ok("audit combined search", request("POST", "/admin/audit-log/search", template="/admin/audit-log/search",
        body={"userId": fixture["user_id"], "userName": USER_NAME, "module": "AUTH",
              "operation": "LOGIN", "status": 0, "clientIp": "198.51.100.31",
              "startTime": "2020-01-01 00:00:00", "endTime": now, "limit": 20}, token=admin_access))
    assert search["data"] and all(row["status"] == 0 and row["module"] == "AUTH" for row in search["data"])
    rejected("reject empty audit search", request("POST", "/admin/audit-log/search", body={}, token=admin_access))
    rejected("reject invalid audit status", request("POST", "/admin/audit-log/search", body={"status": 2}, token=admin_access))
    ok("login history", request("GET", f"/admin/audit-log/login-history/{USER_NAME}?limit=10", template="/admin/audit-log/login-history/{userName}", token=admin_access))
    ok("login failure count", request("GET", f"/admin/audit-log/login-failed/count/{USER_NAME}?minutes=60", template="/admin/audit-log/login-failed/count/{userName}", token=admin_access))
    ok("IP login failure count", request("POST", "/admin/audit-log/login-failed/count/ip", template="/admin/audit-log/login-failed/count/ip", body={"clientIp": "198.51.100.31", "minutes": 60}, token=admin_access))
    rejected("reject unsafe audit cleanup input", request("POST", "/admin/audit-log/clean", body={}, token=admin_access))
    ok("safe audit cleanup", request("POST", "/admin/audit-log/clean", template="/admin/audit-log/clean", body={"beforeDays": 36500}, token=admin_access))

    # OAuth state, callback failure containment, bind and unbind.
    providers = ok("OAuth providers", request("GET", "/auth/oauth2/providers", template="/auth/oauth2/providers"))
    assert any(row["provider"] == PROVIDER for row in providers["data"])
    authorization = ok("OAuth authorization", request("GET", f"/auth/oauth2/authorize/{PROVIDER}", template="/auth/oauth2/authorize/{provider}"))
    state = authorization["data"]["state"]
    rejected("OAuth upstream failure is contained", request("GET", f"/auth/oauth2/callback/{PROVIDER}?code=dummy&state={state}", template="/auth/oauth2/callback/{provider}"), allowed_server_statuses={502})
    rejected("OAuth state cannot be replayed", request("GET", f"/auth/oauth2/callback/{PROVIDER}?code=dummy&state={state}"))
    ok("bind OAuth account", request("POST", "/auth/oauth2/bind", template="/auth/oauth2/bind", body={"oauthUserId": fixture["oauth_user_id"]}, token=admin_access))
    ok("bound OAuth accounts", request("GET", "/auth/oauth2/bound-accounts", template="/auth/oauth2/bound-accounts", token=admin_access))
    ok("unbind OAuth account", request("DELETE", f"/auth/oauth2/unbind/{PROVIDER}", template="/auth/oauth2/unbind/{provider}", token=admin_access))

    # Idempotency and signature demonstration endpoints.
    idempotent = ok("idempotency token", request("GET", "/test/idempotent/token", template="/test/idempotent/token", token=admin_access))
    idem_token = idempotent["data"]["token"]
    fixture["idem_token"] = idem_token
    order = {"orderId": PREFIX, "amount": 9.9, "productName": "fixture"}
    ok("idempotent submit", request("POST", "/test/idempotent/submit", template="/test/idempotent/submit", body=order, token=admin_access, headers={"Idempotent-Token": idem_token}))
    rejected("duplicate idempotent submit", request("POST", "/test/idempotent/submit", body=order, token=admin_access, headers={"Idempotent-Token": idem_token}))
    payment = {"paymentId": PREFIX, "amount": 8.8, "paymentMethod": "TEST"}
    ok("automatic idempotency", request("POST", "/test/idempotent/auto", template="/test/idempotent/auto", body=payment, token=admin_access))
    rejected("duplicate automatic idempotency", request("POST", "/test/idempotent/auto", body=payment, token=admin_access))
    ok("signature endpoint", request("POST", "/test/signature/verify", template="/test/signature/verify", body={"data": PREFIX}, token=admin_access))

    # Password change is last because it invalidates all previous user tokens.
    rejected("reject weak new password", request("POST", "/user/change-password", body={"oldPassword": USER_PASSWORD, "newPassword": "short"}, token=user_access))
    ok("change password", request("POST", "/user/change-password", template="/user/change-password", body={"oldPassword": USER_PASSWORD, "newPassword": NEW_USER_PASSWORD}, token=user_access))
    rejected("old password no longer works", request("POST", "/auth/login", body={"userName": USER_NAME, "password": USER_PASSWORD}))
    ok("new password works", request("POST", "/auth/login", body={"userName": USER_NAME, "password": NEW_USER_PASSWORD}))

    refreshed = ok("refresh token", request("POST", "/auth/refresh/token", template="/auth/refresh/token", body={"refreshToken": admin_refresh}))
    new_access = refreshed["data"]["accessToken"]
    token_values.extend([new_access, refreshed["data"]["refreshToken"]])
    ok("logout", request("POST", "/auth/logout", template="/auth/logout", body={}, token=new_access))
    assert request("GET", "/test/idempotent/token", token=new_access)[0] == 401


def verify_openapi_coverage() -> None:
    status, document = request("GET", "/v3/api-docs")
    if status != 200:
        raise AssertionError(f"OpenAPI unavailable: HTTP {status}")
    methods = {"get", "post", "put", "delete", "patch"}
    operations = {(method, path) for path, item in document.get("paths", {}).items()
                  for method in item if method in methods
                  if path.startswith(("/admin", "/auth", "/test", "/user"))}
    missing = sorted(operations - covered)
    if missing:
        raise AssertionError("controller operations missing from integration suite: " + repr(missing))
    print(f"PASS OpenAPI coverage: {len(operations)} controller operations", flush=True)


if __name__ == "__main__":
    if urllib.parse.urlparse(BASE).hostname not in {"127.0.0.1", "localhost"} and os.getenv("ALLOW_REMOTE_TEST") != "1":
        raise SystemExit("Refusing non-local target; set ALLOW_REMOTE_TEST=1 explicitly")
    try:
        seed()
        exercise()
        verify_openapi_coverage()
    finally:
        cleanup()
