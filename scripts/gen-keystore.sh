#!/bin/bash
# 生成自签 PKCS12 证书（开发 / 测试环境）
# 生产环境：替换为 Let's Encrypt / 企业 CA 颁发的证书
#
# 用法：
#   chmod +x scripts/gen-keystore.sh
#   ./scripts/gen-keystore.sh
#
# 输出：src/main/resources/ssl/gateway.p12

set -e

KEYSTORE_PATH="src/main/resources/ssl/gateway.p12"
ALIAS="gateway"
PASSWORD="${SSL_KEY_STORE_PASSWORD:-changeit}"
VALIDITY=3650   # 10年（仅开发用）
CN="localhost"  # 生产时替换为你的域名，如：api.yourdomain.com

echo "生成自签证书 → $KEYSTORE_PATH"
echo "  CN=$CN  有效期=${VALIDITY}天"

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -sigalg SHA256withRSA \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -validity "$VALIDITY" \
  -dname "CN=$CN, OU=SAPiece, O=SAPiece Nova, L=Beijing, ST=Beijing, C=CN" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

echo "✓ 证书已生成：$KEYSTORE_PATH"
echo "  密码：$PASSWORD（通过环境变量 SSL_KEY_STORE_PASSWORD 覆盖）"
echo ""
echo "启用 HTTPS："
echo "  export SSL_ENABLED=true"
echo "  export SSL_KEY_STORE_PASSWORD=$PASSWORD"
echo "  java -jar target/SAPiece-Gateway-*.jar"
