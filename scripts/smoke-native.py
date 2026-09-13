#!/usr/bin/env python3
"""Run on ubuntu-server after native deployment; create and remove a dedicated test user.
Requires python3-bcrypt and Docker access. Never prints passwords or bearer tokens.
"""
import concurrent.futures
import hashlib
import json
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import bcrypt

BASE = 'http://127.0.0.1:8096'
user_name = 'native_smoke_' + secrets.token_hex(6)
password = secrets.token_hex(24)
tokens = []
user_id = None
route_id = None
upstream = "gateway-smoke-" + secrets.token_hex(6)
upstream_started = False

def sql(statement):
    result = subprocess.run(['docker', 'exec', '-i', 'mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N sapiece_gateway'],
        input=statement, text=True, capture_output=True, check=True)
    return result.stdout.strip()

def http(path, body=None, token=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(BASE + path, headers=headers,
            data=json.dumps(body).encode() if body is not None else None, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            data = response.read()
            try:
                body = json.loads(data) if data else {}
            except json.JSONDecodeError:
                body = {'raw': data.decode()}
            return response.status, body
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def check(condition, label):
    if not condition: raise AssertionError(label)
    print('PASS', label, flush=True)

try:
    status, health = http('/actuator/health/readiness')
    check(status == 200 and health.get('status') == 'UP', 'native readiness (Redis + R2DBC)')
    status, health = http('/actuator/health')
    check('components' not in health, 'anonymous health hides internal details')
    check(http('/actuator/metrics')[0] == 401, 'anonymous metrics rejected')
    check(http('/test/idempotent/token')[0] == 401, 'private endpoint requires access token')
    hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
    sql(f"INSERT INTO sys_user(user_name,nick_name,password,status,del_flag,password_last_changed_at) VALUES ('{user_name}','native smoke','{hashed}',1,0,UTC_TIMESTAMP() - INTERVAL 1 DAY);")
    user_id = int(sql(f"SELECT id FROM sys_user WHERE user_name='{user_name}';"))
    status, result = http('/auth/login', {'userName': user_name, 'password': password})
    check(status == 200 and result.get('code') == 200, 'login via native BCrypt + MySQL + JWT')
    pair = result['data']; access = pair['accessToken']; refresh = pair['refreshToken']; tokens.extend([access, refresh])
    check(http('/test/idempotent/token', token=access)[0] == 200, 'access token authenticates (DB fallback, Redis idempotency)')
    check(http('/test/idempotent/token', token=refresh)[0] == 401, 'refresh token cannot authenticate private endpoint')
    subprocess.run(['docker','exec','redis','redis-cli','SADD',f'user:roles:{user_id}','ADMIN'],check=True,stdout=subprocess.DEVNULL)
    subprocess.run(['docker','exec','redis','redis-cli','SADD',f'user:permissions:{user_id}',
                    'gateway:route:add','gateway:route:delete'],check=True,stdout=subprocess.DEVNULL)
    subprocess.run(['docker','run','-d','--rm','--name',upstream,'--network','middleware-net','nginx:stable'],
                   check=True,stdout=subprocess.DEVNULL)
    upstream_started = True
    route_id = user_name + '_route'
    route = dict(routeId=route_id,routeName='native smoke route',uri='http://' + upstream,
                 predicates=json.dumps([{'name':'Path','args':{'pattern':'/_native-smoke/' + route_id}}]),
                 filters=json.dumps([{'name':'SetPath','args':{'template':'/'}}]),
                 orderNum=0,requireAuth=0,permissionLogic='OR',rateLimitEnabled=0,rateLimitQps=100,
                 rateLimitStrategy='ip',cacheEnabled=0,cacheTtl=300,retryEnabled=0,retryTimes=0,
                 timeoutMs=30000,status=1)
    _, result = http('/admin/route', route, access)
    check(result.get('code') == 200, 'native route creation + method authorization + entity binding')
    forwarded = False
    for _ in range(20):
        status, body = http('/_native-smoke/' + route_id)
        if status == 200 and 'Welcome to nginx' in body.get('raw',''):
            forwarded = True
            break
        time.sleep(0.5)
    check(forwarded, 'dynamic database route forwards to real upstream')
    _, result = http('/admin/route/routeId/' + route_id, token=access, method='DELETE')
    check(result.get('code') == 200, 'native dynamic route removal')

    status, result = http('/auth/refresh', {}, access)
    check(result.get('code') == 400, 'legacy refresh bypass closed')
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        attempts = list(pool.map(lambda _: http('/auth/refresh/token', {'refreshToken': refresh}), range(2)))
    successes = [body for _, body in attempts if body.get('code') == 200]
    check(len(successes) == 1 and sum(body.get('code') == 401 for _, body in attempts) == 1,
          'concurrent refresh accepts exactly one request')
    new_pair = successes[0]['data']; tokens.extend(new_pair.values())
    _, result = http('/auth/logout', {}, new_pair['accessToken'])
    check(result.get('code') == 200, 'logout writes revocation to Redis')
    check(http('/test/idempotent/token', token=new_pair['accessToken'])[0] == 401, 'revoked token rejected')
    sql(f'UPDATE sys_user SET status=0 WHERE id={user_id};')
    _, result = http('/auth/refresh/token', {'refreshToken': new_pair['refreshToken']})
    check(result.get('code') == 401, 'disabled user cannot refresh')
finally:
    if route_id is not None:
        sql(f"DELETE FROM sys_gateway_route WHERE route_id='{route_id}';")
    if upstream_started:
        subprocess.run(['docker','stop',upstream],stdout=subprocess.DEVNULL,check=True)
    if user_id is not None:
        sql(f'DELETE FROM sys_audit_log WHERE user_id={user_id}; DELETE FROM sys_user WHERE id={user_id};')
        keys = [f'user:roles:{user_id}', f'user:permissions:{user_id}', f'user:pwdver:{user_id}']
        keys += ['token:blacklist:' + hashlib.md5(token.encode()).hexdigest() for token in tokens]
        subprocess.run(['docker', 'exec', 'redis', 'redis-cli', 'DEL', *keys], check=True, stdout=subprocess.DEVNULL)
        print('CLEANUP test user and test token cache removed', flush=True)
