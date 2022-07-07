pipeline {
    agent {
        label "swarm"
    }
    parameters {
        choice(name: 'OPENJDK_TAG', choices: [ \
            '8-jdk-slim', \
            '11-jdk-slim', \
            '12-jdk-alpine', \
            '16-jdk-slim', \
            '17-jdk-slim', \
        ], description: 'Tag from official OpenJDK images, like openjdk:16-jdk-slim')
    }
    environment {
        ECR_REGISTRY = "592754518446.dkr.ecr.us-west-2.amazonaws.com"
        REPO_NAME = "shipper-java"
        JAVA_VERSION = "${ OPENJDK_TAG.split('-')[0] }"
        TAG = "${ JAVA_VERSION }"
    }
    stages {
        stage('Docker build') {
            steps {
                script {
                    currentBuild.displayName = "#${currentBuild.number} $REPO_NAME:$TAG -- $OPENJDK_TAG"
                    currentBuild.description = """OPENJDK_TAG: $OPENJDK_TAG
JAVA_VERSION: $JAVA_VERSION
IMAGE: $REPO_NAME:$TAG"""
                }

                // Build and push the Docker image to ECR
                echo "Build the Docker image..."
                echo "OPENJDK_TAG: ${ OPENJDK_TAG}, JAVA_VERSION: ${ JAVA_VERSION }, TAG: ${ TAG }"
                sh "aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                sh "docker build --build-arg OPENJDK_TAG -f base-images/${REPO_NAME}.Dockerfile -t ${ECR_REGISTRY}/${REPO_NAME}:${TAG} ."
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
