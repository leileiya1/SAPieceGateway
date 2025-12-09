pipeline {
    agent any

    environment {
        GIT_REPO        = 'git@github.com:leileiya1/SAPieceGateway.git'
        GIT_CREDENTIALS = 'jenkins-server'
        SSH_CREDENTIALS = 'jenkins-ubuntu'
        REMOTE_HOST     = '10.70.239.17'
        REMOTE_USER     = 'sapiece'
        REMOTE_DIR      = '/opt/software/sapiece-server/gateway'
        IMAGE_NAME      = 'sapiece-gateway:latest'
        CONTAINER_NAME  = 'sapiece-gateway'
        BASE_IMAGE      = 'jokers/jre21:alpine3'
    }

    stages {
        stage('Checkout') {
            steps {
                git url: env.GIT_REPO, credentialsId: env.GIT_CREDENTIALS, branch: 'main'
            }
        }

        stage('Build') {
            steps {
                echo '[INFO] Starting Maven build'
                sh 'mvn -B -ntp clean package -DskipTests'
                echo '[INFO] Maven build finished'
            }
        }

        stage('Deploy to Server') {
            steps {
                script {
                    String jarFile = sh(
                            script: "ls target/*.jar | grep -v original | head -n 1",
                            returnStdout: true
                    ).trim()

                    if (!jarFile) {
                        error 'No packaged JAR found. Check Maven build output.'
                    }

                    echo "[INFO] Deploying artifact: ${jarFile}"

                    sshagent([env.SSH_CREDENTIALS]) {
                        sh """
                            echo "[INFO] Connecting to ${env.REMOTE_HOST} and uploading artifact"
                            ssh -o StrictHostKeyChecking=no ${env.REMOTE_USER}@${env.REMOTE_HOST} "mkdir -p ${env.REMOTE_DIR}"
                            scp -o StrictHostKeyChecking=no ${jarFile} ${env.REMOTE_USER}@${env.REMOTE_HOST}:${env.REMOTE_DIR}/app.jar
                            ssh -o StrictHostKeyChecking=no ${env.REMOTE_USER}@${env.REMOTE_HOST} <<EOF
set -e
cd ${REMOTE_DIR}

echo "[INFO] \$(date '+%F %T') - Deployment started in ${REMOTE_DIR}"
ls -al

cat <<'DOCKER' > Dockerfile
FROM ${BASE_IMAGE}
WORKDIR /opt/app
ARG JAR_FILE=app.jar
COPY \${JAR_FILE} /opt/app/app.jar
COPY skywalking /opt/app/skywalking
EXPOSE 9550
ENTRYPOINT ["sh","-c","java -javaagent:/opt/app/skywalking/java-agent.jar -jar /opt/app/app.jar"]
DOCKER

echo "[INFO] Checking old container ${CONTAINER_NAME}"
if docker ps -a --format '{{.Names}}' | grep -w ${CONTAINER_NAME} >/dev/null 2>&1; then
    docker stop ${CONTAINER_NAME} || true
    docker rm ${CONTAINER_NAME} || true
else
    echo "[INFO] No existing container named ${CONTAINER_NAME}"
fi

echo "[INFO] Checking old image ${IMAGE_NAME}"
if [ -n "\$(docker images -q ${IMAGE_NAME})" ]; then
    docker rmi ${IMAGE_NAME} || true
else
    echo "[INFO] No existing image named ${IMAGE_NAME}"
fi

echo "[INFO] Building Docker image ${IMAGE_NAME}"
docker build -t ${IMAGE_NAME} --build-arg JAR_FILE=app.jar .

echo "[INFO] Starting container ${CONTAINER_NAME} on port 9550"
docker run -d --name ${CONTAINER_NAME} -p 9550:9550 --restart=always ${IMAGE_NAME}
docker ps -f name=${CONTAINER_NAME}
echo "[INFO] Deployment completed successfully"
EOF
                        """
                    }
                }
            }
        }
    }
}
