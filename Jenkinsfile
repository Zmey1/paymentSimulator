pipeline {
    agent any

    options {
        timestamps()
    }

    parameters {
        string(name: 'AWS_REGION', defaultValue: 'ap-south-1', description: 'AWS region for ECR, RDS, and EKS')
        string(name: 'EKS_CLUSTER', defaultValue: 'payments-sim-eks', description: 'EKS cluster name')
        string(name: 'DB_INSTANCE_IDENTIFIER', defaultValue: 'payments-sim-db', description: 'RDS instance identifier created by Terraform')
        string(name: 'DB_USERNAME', defaultValue: 'payments_user', description: 'Application database username')
        string(name: 'KAFKA_BOOTSTRAP_SERVERS', defaultValue: 'kafka.default.svc.cluster.local:9092', description: 'Kafka bootstrap service inside the cluster')
        booleanParam(name: 'RAZORPAY_ENABLED', defaultValue: false, description: 'Enable Razorpay sandbox configuration in payment-service')
        string(name: 'RAZORPAY_MERCHANT_NAME', defaultValue: 'PayFlow Demo', description: 'Merchant label shown in Razorpay checkout')
        string(name: 'RAZORPAY_DESCRIPTION', defaultValue: 'Sandbox Checkout', description: 'Description shown in Razorpay checkout')
        string(name: 'RAZORPAY_RECEIVER_NAME', defaultValue: 'Demo Merchant', description: 'Internal receiver name used after Razorpay verification')
    }

    environment {
        ECR_NAMESPACE = 'payments-sim'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Resolve AWS Runtime') {
            steps {
                script {
                    env.AWS_REGION = params.AWS_REGION.trim()
                    env.EKS_CLUSTER = params.EKS_CLUSTER.trim()
                    env.DB_INSTANCE_IDENTIFIER = params.DB_INSTANCE_IDENTIFIER.trim()
                    env.DB_USERNAME = params.DB_USERNAME.trim()
                    env.KAFKA_BOOTSTRAP_SERVERS = params.KAFKA_BOOTSTRAP_SERVERS.trim()
                    env.RAZORPAY_ENABLED = params.RAZORPAY_ENABLED.toString()
                    env.RAZORPAY_MERCHANT_NAME = params.RAZORPAY_MERCHANT_NAME
                    env.RAZORPAY_DESCRIPTION = params.RAZORPAY_DESCRIPTION
                    env.RAZORPAY_RECEIVER_NAME = params.RAZORPAY_RECEIVER_NAME
                    env.RAZORPAY_KEY_ID = env.RAZORPAY_KEY_ID ?: ''
                    env.RAZORPAY_KEY_SECRET = env.RAZORPAY_KEY_SECRET ?: ''
                    env.AWS_ACCOUNT_ID = sh(script: "aws sts get-caller-identity --query Account --output text --region ${env.AWS_REGION}", returnStdout: true).trim()
                    env.ECR_REGISTRY = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
                    env.RDS_ENDPOINT = sh(
                        script: "aws rds describe-db-instances --db-instance-identifier ${env.DB_INSTANCE_IDENTIFIER} --query 'DBInstances[0].Endpoint.Address' --output text --region ${env.AWS_REGION}",
                        returnStdout: true
                    ).trim()
                }
            }
        }

        stage('Build') {
            parallel {
                stage('Build payment-service') {
                    steps {
                        dir('services/payment-service') {
                            sh 'mvn clean package -DskipTests -B'
                        }
                    }
                }
                stage('Build fraud-detection-service') {
                    steps {
                        dir('services/fraud-detection-service') {
                            sh 'mvn clean package -DskipTests -B'
                        }
                    }
                }
                stage('Build notification-service') {
                    steps {
                        dir('services/notification-service') {
                            sh 'mvn clean package -DskipTests -B'
                        }
                    }
                }
            }
        }

        stage('Unit Tests') {
            parallel {
                stage('Test payment-service') {
                    steps {
                        dir('services/payment-service') {
                            sh 'mvn test -B'
                        }
                    }
                    post {
                        always {
                            junit 'services/payment-service/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('Test fraud-detection-service') {
                    steps {
                        dir('services/fraud-detection-service') {
                            sh 'mvn test -B'
                        }
                    }
                    post {
                        always {
                            junit 'services/fraud-detection-service/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('Test notification-service') {
                    steps {
                        dir('services/notification-service') {
                            sh 'mvn test -B'
                        }
                    }
                    post {
                        always {
                            junit 'services/notification-service/target/surefire-reports/*.xml'
                        }
                    }
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    dir('services/payment-service') {
                        sh 'mvn sonar:sonar -B'
                    }
                    dir('services/fraud-detection-service') {
                        sh 'mvn sonar:sonar -B'
                    }
                    dir('services/notification-service') {
                        sh 'mvn sonar:sonar -B'
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                sh "aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${env.ECR_REGISTRY}"

                dir('services/payment-service') {
                    sh "docker build -t ${env.ECR_REGISTRY}/${env.ECR_NAMESPACE}/payment-service:${env.BUILD_NUMBER} ."
                    sh "docker push ${env.ECR_REGISTRY}/${env.ECR_NAMESPACE}/payment-service:${env.BUILD_NUMBER}"
                }
                dir('services/fraud-detection-service') {
                    sh "docker build -t ${env.ECR_REGISTRY}/${env.ECR_NAMESPACE}/fraud-detection-service:${env.BUILD_NUMBER} ."
                    sh "docker push ${env.ECR_REGISTRY}/${env.ECR_NAMESPACE}/fraud-detection-service:${env.BUILD_NUMBER}"
                }
                dir('services/notification-service') {
                    sh "docker build -t ${env.ECR_REGISTRY}/${env.ECR_NAMESPACE}/notification-service:${env.BUILD_NUMBER} ."
                    sh "docker push ${env.ECR_REGISTRY}/${env.ECR_NAMESPACE}/notification-service:${env.BUILD_NUMBER}"
                }
                dir('payment-dashboard') {
                    sh "docker build -t ${env.ECR_REGISTRY}/${env.ECR_NAMESPACE}/payment-dashboard:${env.BUILD_NUMBER} ."
                    sh "docker push ${env.ECR_REGISTRY}/${env.ECR_NAMESPACE}/payment-dashboard:${env.BUILD_NUMBER}"
                }
            }
        }

        stage('Integration Tests') {
            steps {
                sh 'docker compose up -d'
                sh 'sleep 30'
                sh 'docker compose run --rm integration-tests'
                sh 'docker cp $(docker compose ps -q payment-dashboard | head -1):/dev/null /dev/null 2>/dev/null || true'
            }
            post {
                always {
                    sh '''
                        CONTAINER_ID=$(docker compose ps -aq integration-tests 2>/dev/null | head -1)
                        if [ -n "$CONTAINER_ID" ]; then
                            docker cp "$CONTAINER_ID":/app/target/surefire-reports tests/target/surefire-reports 2>/dev/null || true
                        fi
                    '''
                    sh 'docker compose --profile test down -v'
                    junit allowEmptyResults: true, testResults: 'tests/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Configure kubectl') {
            steps {
                sh "aws eks update-kubeconfig --name ${env.EKS_CLUSTER} --region ${env.AWS_REGION}"
                sh 'kubectl get nodes'
            }
        }

        stage('Deploy Kafka') {
            steps {
                sh '''
                    if ! helm repo list | awk 'NR > 1 {print $1}' | grep -qx bitnami; then
                      helm repo add bitnami https://charts.bitnami.com/bitnami
                    fi
                    helm repo update
                    helm upgrade --install kafka bitnami/kafka --namespace default -f k8s/kafka/helm-values.yml
                '''
            }
        }

        stage('Render Kubernetes Manifests') {
            steps {
                withCredentials([string(credentialsId: 'payments-db-password', variable: 'DB_PASSWORD')]) {
                    withEnv([
                        "ECR_REGISTRY=${env.ECR_REGISTRY}",
                        "IMAGE_TAG=${env.BUILD_NUMBER}",
                        "RDS_ENDPOINT=${env.RDS_ENDPOINT}",
                        "DB_USERNAME=${env.DB_USERNAME}",
                        "KAFKA_BOOTSTRAP_SERVERS=${env.KAFKA_BOOTSTRAP_SERVERS}",
                        "RAZORPAY_ENABLED=${env.RAZORPAY_ENABLED}",
                        "RAZORPAY_KEY_ID=${env.RAZORPAY_KEY_ID}",
                        "RAZORPAY_KEY_SECRET=${env.RAZORPAY_KEY_SECRET}",
                        "RAZORPAY_MERCHANT_NAME=${env.RAZORPAY_MERCHANT_NAME}",
                        "RAZORPAY_DESCRIPTION=${env.RAZORPAY_DESCRIPTION}",
                        "RAZORPAY_RECEIVER_NAME=${env.RAZORPAY_RECEIVER_NAME}"
                    ]) {
                        sh 'bash scripts/render-k8s-manifests.sh'
                    }
                }
            }
        }

        stage('Deploy to EKS') {
            steps {
                sh 'kubectl apply -f .rendered/k8s/configmaps.yml'
                sh 'kubectl apply -f .rendered/k8s/secrets.yml'
                sh 'kubectl apply -f .rendered/k8s/payment-service/service.yml'
                sh 'kubectl apply -f .rendered/k8s/payment-service/deployment.yml'
                sh 'kubectl apply -f .rendered/k8s/fraud-detection-service/service.yml'
                sh 'kubectl apply -f .rendered/k8s/fraud-detection-service/deployment.yml'
                sh 'kubectl apply -f .rendered/k8s/notification-service/service.yml'
                sh 'kubectl apply -f .rendered/k8s/notification-service/deployment.yml'
                sh 'kubectl apply -f .rendered/k8s/payment-dashboard/service.yml'
                sh 'kubectl apply -f .rendered/k8s/payment-dashboard/deployment.yml'

                sh 'kubectl rollout status deployment/payment-service --timeout=180s'
                sh 'kubectl rollout status deployment/fraud-detection-service --timeout=180s'
                sh 'kubectl rollout status deployment/notification-service --timeout=180s'
                sh 'kubectl rollout status deployment/payment-dashboard --timeout=180s'
                sh 'kubectl get svc payment-dashboard'
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully.'
        }
        failure {
            echo 'Pipeline failed.'
        }
        always {
            archiveArtifacts artifacts: '.rendered/k8s/**/*.yml', allowEmptyArchive: true
        }
    }
}
