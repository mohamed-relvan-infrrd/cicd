pipeline {
    agent {
        label "swarm"
    }
    parameters {
        choice(name: 'MAVEN_TAG', choices: [ \
            '3.8-openjdk-17', \
            '3.8-openjdk-16', \
            '3.8-openjdk-15', \
            '3.8-jdk-11',     \
            '3.8-jdk-8',      \
            '3.6-jdk-12',      \
            '3.8-openjdk-8',      \
        ], description: 'Tag from official maven images')
    }
    environment {
        ECR_REGISTRY = "592754518446.dkr.ecr.us-west-2.amazonaws.com"
        REPO_NAME = "builder-maven-java"
        MAVEN_TAG = "${params.MAVEN_TAG}"
        TAG = "${MAVEN_TAG}"
    }
    stages {
        stage('ECR Docker login'){
            steps {
                sh "aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
            }
        }
        stage('Build app image from dockerfile'){
            steps {
                script {
                    currentBuild.displayName = "#${currentBuild.number} $REPO_NAME:$TAG"
                    currentBuild.description = """MAVEN_TAG: $MAVEN_TAG
IMAGE: $REPO_NAME:$TAG"""
                }

                script {
                    docker.withRegistry("https://${ECR_REGISTRY}") {
                        app_image = docker.build("${REPO_NAME}:${TAG}", \
                            "-f base-images/${REPO_NAME}.Dockerfile \
                             --build-arg MAVEN_TAG \
                             base-images")
                    }
                }
            }
        }
        stage('Push app image to ECR') {
            steps {
                echo "Push the Docker image to ECR..."
                script {
                    docker.withRegistry("https://${ECR_REGISTRY}"){
                        app_image.push()
                    }
                }
            }
        }
        stage('Remove app image from Local') {
            steps {
                echo "Remove the Docker image from Local..."
                //#TODO Kabul: Please review later
                sh "docker rmi ${ECR_REGISTRY}/${REPO_NAME}:${TAG}"
            }
        }
    }
}
