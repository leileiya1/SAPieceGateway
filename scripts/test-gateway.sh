#!/bin/bash
# 网关接口测试脚本
# 用法：bash scripts/test-gateway.sh
# 需要先把 TOKEN 替换为登录后拿到的 access_token

GW="http://10.70.239.17:8080"
TOKEN=""   # ← 登录后填入

ok()   { echo "  [✓] $1"; }
fail() { echo "  [✗] $1"; }
section() { echo ""; echo "━━ $1 ━━"; }

check() {
  local label="$1"
  local status
  status=$(eval "$2" -o /dev/null -w "%{http_code}" -s --max-time 5)
  if [[ "$status" == "200" || "$status" == "201" ]]; then
    ok "$label → HTTP $status"
  else
    fail "$label → HTTP $status"
  fi
}

check_auth() {
  local label="$1"
  local status
  status=$(eval "$2" -H "\"Authorization: Bearer $TOKEN\"" -o /dev/null -w "%{http_code}" -s --max-time 5)
  if [[ "$status" == "200" || "$status" == "201" ]]; then
    ok "$label → HTTP $status"
  elif [[ "$status" == "401" ]]; then
    fail "$label → 401 (TOKEN 未填写或已过期)"
  else
    ok "$label → HTTP $status"
  fi
}

# ── 1. 健康检查 ──────────────────────────────────────────────
section "1. 网关健康检查"
check "actuator/health" "curl '$GW/actuator/health'"

# ── 2. 认证（获取 Token）─────────────────────────────────────
section "2. 获取 Token（请先确认有效账号）"
echo "  执行登录:"
RESP=$(curl -s -X POST "$GW/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
echo "  响应: $RESP"
TOKEN=$(echo "$RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then
  ok "登录成功，Token: ${TOKEN:0:30}..."
else
  fail "登录失败，后续需要 Token 的测试将显示 401"
fi

# ── 3. 公开接口（无需 Token）─────────────────────────────────
section "3. 公开接口（无需登录）"
check "验证码发送" "curl -X POST '$GW/v1/notification/captcha' -H 'Content-Type: application/json' -d '{\"type\":\"EMAIL\",\"target\":\"test@test.com\"}'"
check "支付回调存活" "curl -X POST '$GW/v1/payments/alipay/notify' -H 'Content-Type: application/x-www-form-urlencoded' -d 'out_trade_no=test'"

# ── 4. 需要登录的接口 ─────────────────────────────────────────
section "4. 业务接口（需要 Token）"
check_auth "获取通知列表"     "curl '$GW/v1/notification/inbox'"
check_auth "未读数量"         "curl '$GW/v1/notification/unread-count'"
check_auth "帖子列表"         "curl '$GW/v1/posts'"
check_auth "帖子标签"         "curl '$GW/v1/tags'"
check_auth "搜索"             "curl '$GW/v1/search?q=AI'"
check_auth "课程列表"         "curl '$GW/v1/courses'"
check_auth "课程分类"         "curl '$GW/v1/course-categories'"
check_auth "会员套餐列表"     "curl '$GW/v1/membership-plans'"
check_auth "我的会员"         "curl '$GW/v1/my/membership'"
check_auth "我的作品集"       "curl '$GW/v1/portfolio/me'"
check_auth "任务目录"         "curl '$GW/v1/missions/catalog'"
check_auth "我的任务进度"     "curl '$GW/v1/missions/me/progress'"
check_auth "项目列表"         "curl '$GW/v1/projects'"
check_auth "学习进度"         "curl '$GW/v1/learning/progress'"
check_auth "订单列表"         "curl '$GW/v1/orders'"
check_auth "聊天会话列表"     "curl '$GW/api/conversations'"
check_auth "仪表盘-创作者"   "curl '$GW/v1/dashboard/creator/overview'"
check_auth "仪表盘-成长教练" "curl '$GW/v1/dashboard/growth-coach/overview'"
check_auth "举报列表"         "curl '$GW/v1/reports/me'"
check_auth "屏蔽列表"         "curl '$GW/v1/blocks'"
check_auth "处罚记录"         "curl '$GW/v1/penalties/me'"

# ── 5. 管理员接口 ─────────────────────────────────────────────
section "5. 管理员接口（需要 admin 权限 Token）"
check_auth "路由列表"         "curl '$GW/admin/route/list'"
check_auth "事件管理"         "curl '$GW/admin/v1/events'"

echo ""
echo "━━ 测试完成 ━━"
