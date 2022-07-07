pipeline {
    agent {
        label "swarm"
    }
    parameters {
        // string(name: 'BRANCH', defaultValue: 'none',
            // description: '''(optional) TBD''')
        choice(name: 'PYTHON_VERSION', choices: ['3.6.8','3.6.9','3.7', '3.8'],
            description: 'Major Python version to build from')
    }
    environment {
        ECR_REGISTRY = "592754518446.dkr.ecr.us-west-2.amazonaws.com"
        REPO_NAME = "builder-python"
        TAG = "${params.PYTHON_VERSION}"
    }
    stages {
        stage('Docker build') {
            steps {
                script {
                    currentBuild.displayName = "#${currentBuild.number} $REPO_NAME:$TAG -- $PYTHON_VERSION"
                    currentBuild.description = """PYTHON_VERSION: $PYTHON_VERSION
IMAGE: $REPO_NAME:$TAG"""
                }

                // Build and push the Docker image to ECR
                echo "Build the Docker image..."
                sh "aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                sh "docker build -f base-images/${REPO_NAME}.Dockerfile -t ${ECR_REGISTRY}/${REPO_NAME}:${TAG} ."
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
