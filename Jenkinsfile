pipeline {
    agent { label 'school-linux' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
        disableConcurrentBuilds(abortPrevious: false)
        skipDefaultCheckout(true)
        timestamps()
        timeout(time: 120, unit: 'MINUTES')
    }

    triggers {
        githubPush()
        // GitHub 无法回调内网 Jenkins 时，最多约 5 分钟也会发现新提交。
        pollSCM('H/5 * * * *')
    }

    environment {
        KUBECTL = 'sudo /usr/local/bin/k3s kubectl'
        UBUNTU_K3S_HOST = 'jenkins@10.65.13.94'
        NATIVE_BUILD_CPUS = '12'
        DOCKER_BUILDKIT = '1'
        BUILDKIT_PROGRESS = 'plain'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.REVISION = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.REVISION}"
                }
            }
        }

        stage('Validate') {
            steps {
                sh 'scripts/ci/validate.sh'
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml' }
            }
        }

        stage('Build immutable images') {
            steps {
                sh 'scripts/ci/build-images.sh "$REVISION"'
                archiveArtifacts artifacts: "dist/sapiece-images-${REVISION}.tar.gz.sha256", fingerprint: true
            }
        }

        stage('Distribute to both K3s nodes') {
            steps {
                sh 'scripts/ci/distribute-images.sh "$REVISION" "$UBUNTU_K3S_HOST"'
            }
        }

        stage('Deploy K3s') {
            steps {
                sh 'scripts/ci/deploy.sh "$REVISION"'
            }
        }

        stage('Integration and resilience') {
            steps {
                sh 'scripts/ci/integration-test.sh'
            }
        }
    }

    post {
        unsuccessful {
            sh 'scripts/ci/rollback.sh || true'
        }
        always {
            archiveArtifacts allowEmptyArchive: true,
                    artifacts: 'dist/deployed-images.txt,dist/previous-images.txt,dist/integration.log',
                    fingerprint: true
            deleteDir()
        }
    }
}
