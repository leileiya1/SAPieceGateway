pipeline {
    agent any

    options {
        // 🧾 控制 Jenkins 构建记录/产物保留，避免长期堆积
        buildDiscarder(logRotator(
                numToKeepStr: '20',
                artifactNumToKeepStr: '10'
        ))

        // ⏱️ 时间戳更易排查日志
        timestamps()

        // 🚫 避免并发部署出现互相覆盖
        disableConcurrentBuilds()
    }

    environment {
        // ===== Git / SSH =====
        GIT_REPO        = 'git@github.com:leileiya1/SAPieceGateway.git'
        GIT_CREDENTIALS = 'jenkins-ricegrow'
        SSH_CREDENTIALS = 'ubuntu-server'

        // ===== Remote Server =====
        REMOTE_HOST     = '10.70.239.17'
        REMOTE_USER     = 'sapiece'
        REMOTE_DIR      = '/opt/software/sapiece-server/gateway'

        // ===== Docker =====
        IMAGE_NAME      = 'sapiece-gateway:latest'
        CONTAINER_NAME  = 'sapiece-gateway'
        BASE_IMAGE      = 'jokers/jre21:alpine3'

        // ===== SkyWalking =====
        SKYWALKING_AGENT_DIR = '/opt/software/sapiece-server/gateway/skywalking-agent'
        SW_BACKEND_SERVICES  = '10.70.239.17:11800'
        SW_AGENT_NAME        = 'sapiece-gateway'
    }

    stages {

        stage('🧩 Checkout') {
            steps {
                echo "📥 正在拉取代码..."
                git url: env.GIT_REPO, credentialsId: env.GIT_CREDENTIALS, branch: 'main'
                echo "✅ 代码拉取完成"
            }
        }

        stage('🔧 Build (Maven)') {
            steps {
                echo "🧪 开始 Maven 构建..."
                sh '''
                    set -e
                    echo "📌 当前工作目录: $(pwd)"
                    echo "📌 Java 版本:"
                    java -version || true
                    echo "📌 Maven 版本:"
                    mvn -v || true

                    echo "🏗️ 执行打包..."
                    mvn -B -ntp clean package -DskipTests

                    echo "📦 构建产物预览:"
                    ls -al target || true
                '''
                echo "✅ Maven 构建完成"
            }
        }

        stage('🚀 Deploy to Server') {
            steps {
                script {
                    String jarFile = sh(
                            script: "ls target/*.jar | grep -v original | head -n 1",
                            returnStdout: true
                    ).trim()

                    if (!jarFile) {
                        error '❌ 未找到可部署的 JAR，请检查 Maven 构建输出'
                    }

                    echo "📦 准备部署 JAR: ${jarFile}"
                    echo "🖥️ 目标服务器: ${env.REMOTE_USER}@${env.REMOTE_HOST}"
                    echo "📁 目标目录: ${env.REMOTE_DIR}"
                    echo "🧠 SkyWalking 后端: ${env.SW_BACKEND_SERVICES}"

                    sshagent([env.SSH_CREDENTIALS]) {
                        sh """
                            set -e
                            echo "🔐 建立远程目录..."
                            ssh -o StrictHostKeyChecking=no ${env.REMOTE_USER}@${env.REMOTE_HOST} "mkdir -p ${env.REMOTE_DIR}"

                            echo "📤 上传 app.jar..."
                            scp -o StrictHostKeyChecking=no ${jarFile} ${env.REMOTE_USER}@${env.REMOTE_HOST}:${env.REMOTE_DIR}/app.jar

                            echo "🧩 进入远程部署脚本..."
                            ssh -o StrictHostKeyChecking=no ${env.REMOTE_USER}@${env.REMOTE_HOST} <<'EOF'
set -e

echo "=============================="
echo "🚀 远程部署开始: \$(date '+%F %T')"
echo "=============================="

REMOTE_DIR="${REMOTE_DIR}"
IMAGE_NAME="${IMAGE_NAME}"
CONTAINER_NAME="${CONTAINER_NAME}"
BASE_IMAGE="${BASE_IMAGE}"

SW_AGENT_NAME="${SW_AGENT_NAME}"
SW_BACKEND_SERVICES="${SW_BACKEND_SERVICES}"

cd ${REMOTE_DIR}

echo "📁 当前目录: \$(pwd)"
echo "📦 文件列表:"
ls -al

echo "🔍 检查 SkyWalking Agent 目录..."
if [ ! -d "skywalking-agent" ]; then
    echo "❌ 未发现 skywalking-agent 目录"
    echo "👉 期望路径: ${REMOTE_DIR}/skywalking-agent"
    exit 1
fi

if [ ! -f "skywalking-agent/skywalking-agent.jar" ]; then
    echo "❌ 未发现 skywalking-agent.jar"
    echo "👉 期望文件: ${REMOTE_DIR}/skywalking-agent/skywalking-agent.jar"
    exit 1
fi
echo "✅ SkyWalking Agent 检查通过"

echo "📝 生成 Dockerfile..."
cat <<DOCKER > Dockerfile
FROM ${BASE_IMAGE}
WORKDIR /opt/app
ARG JAR_FILE=app.jar

COPY \${JAR_FILE} /opt/app/app.jar
COPY skywalking-agent /opt/app/skywalking-agent

EXPOSE 9550

ENTRYPOINT ["sh","-c","java -javaagent:/opt/app/skywalking-agent/skywalking-agent.jar -jar /opt/app/app.jar"]
DOCKER

echo "🧹 停止并删除旧容器(若存在)..."
if docker ps -a --format '{{.Names}}' | grep -w ${CONTAINER_NAME} >/dev/null 2>&1; then
    docker stop ${CONTAINER_NAME} || true
    docker rm ${CONTAINER_NAME} || true
    echo "✅ 旧容器已清理"
else
    echo "ℹ️ 未发现旧容器"
fi

echo "🧽 删除旧镜像(若存在)..."
if [ -n "\$(docker images -q ${IMAGE_NAME})" ]; then
    docker rmi ${IMAGE_NAME} || true
    echo "✅ 旧镜像已清理"
else
    echo "ℹ️ 未发现旧镜像"
fi

echo "🏗️ 构建新镜像: ${IMAGE_NAME}"
docker build -t ${IMAGE_NAME} --build-arg JAR_FILE=app.jar .

echo "🚢 启动新容器: ${CONTAINER_NAME}"
docker run -d --name ${CONTAINER_NAME} \\
  -p 9550:9550 \\
  --restart=always \\
  -e SW_AGENT_NAME=\${SW_AGENT_NAME} \\
  -e SW_AGENT_COLLECTOR_BACKEND_SERVICES=\${SW_BACKEND_SERVICES} \\
  ${IMAGE_NAME}

echo "📌 容器状态:"
docker ps -f name=${CONTAINER_NAME}

echo "=============================="
echo "✅ 远程部署完成: \$(date '+%F %T')"
echo "=============================="
EOF
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            echo "🧹 开始清理 Jenkins 构建残留（防止磁盘爆炸）..."

            script {
                // 1) 优先尝试 Workspace Cleanup 插件
                try {
                    cleanWs(deleteDirs: true, disableDeferredWipeout: true)
                    echo "✅ cleanWs 工作区清理完成"
                } catch (err) {
                    // 2) 如果没装插件，用内置 deleteDir 兜底
                    echo "⚠️ cleanWs 不可用，使用 deleteDir() 兜底清理"
                    deleteDir()
                    echo "✅ deleteDir() 工作区清理完成"
                }
            }

            echo "🧽 清理提示：构建记录/产物保留策略已启用（最多保留 20 次构建，10 份产物）"
            echo "🏁 本次流水线结束"
        }

        success {
            echo "🎉 部署成功！"
        }

        failure {
            echo "❌ 部署失败！请根据上方日志定位问题"
        }
    }
}
