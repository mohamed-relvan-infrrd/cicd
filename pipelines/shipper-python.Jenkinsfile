pipeline {
    agent {
        label "swarm"
    }
    parameters {
        choice(name: 'TAG', choices: [
          '3.6-alpine3.12',
          '3.6-alpine3.14',
          '3.7-alpine3.12',
          '3.7-alpine3.14',
          '3.8-alpine3.14',
          '3.9-alpine3.14'
        ],
        description: 'Python version to build from, like 3.7-alpine3.14')
    }
    environment {
        ECR_REGISTRY = "592754518446.dkr.ecr.us-west-2.amazonaws.com"
        REPO_NAME = "shipper-python"
    }
    stages {
        stage('Docker build') {
            steps {
                script {
                    currentBuild.displayName = "#${currentBuild.number} $REPO_NAME:$TAG -- $TAG"
                    currentBuild.description = """TAG: $TAG
IMAGE: $REPO_NAME:$TAG"""
                }

                // Build and push the Docker image to ECR
                echo "Build the Docker image..."
                sh "aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                sh "docker build --build-arg TAG -f base-images/${REPO_NAME}.Dockerfile -t ${ECR_REGISTRY}/${REPO_NAME}:${TAG} ."
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
