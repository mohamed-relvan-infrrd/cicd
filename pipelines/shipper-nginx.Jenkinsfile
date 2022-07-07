pipeline {
    agent {
        label "swarm"
    }
    parameters {
        choice(name: 'NGINX_TAG', choices: [ \
            '1.21.3', \
            // '1.21.3-alpine', \
            // alpine requires the Dockerfile to use apk instead of apt-get
        ], description: 'Tag from official NGINX dockerhub images')
    }
    environment {
        ECR_REGISTRY = "592754518446.dkr.ecr.us-west-2.amazonaws.com"
        REPO_NAME = "shipper-nginx"
        TAG = "${ NGINX_TAG }"
    }
    stages {
        stage('Docker build') {
            steps {
                script {
                    currentBuild.displayName = "#${currentBuild.number} $REPO_NAME:$TAG -- $NGINX_TAG"
                    currentBuild.description = """NGINX_TAG: $NGINX_TAG
IMAGE: $REPO_NAME:$TAG"""
                }

                // Build and push the Docker image to ECR
                echo "Build the Docker image..."
                sh "aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                sh "docker build --build-arg NGINX_TAG -f base-images/${REPO_NAME}.Dockerfile -t ${ECR_REGISTRY}/${REPO_NAME}:${TAG} ."
            }
        }
        stage('Push to ECR') {
            steps {
                echo "Push the Docker image to ECR..."
                sh "docker push ${ECR_REGISTRY}/${REPO_NAME}:${TAG}"
                sh "docker rmi ${ECR_REGISTRY}/${REPO_NAME}:${TAG}"
            }
        }
    }
}
