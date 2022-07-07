def call(Map<String,String> pipeargs) {
    String dockerImageName = pipeargs.dockerImageName
    String dockerfilePath  = pipeargs.getOrDefault("dockerfilePath", "./Dockerfile")
    String ecrRegistry     = pipeargs.getOrDefault("ecrRegistryOverride", "592754518446.dkr.ecr.us-west-2.amazonaws.com" )
    String slackChannel    = pipeargs.getOrDefault("slackChannelOverride", "#polaris-cicd")
    String autoDeploy      = pipeargs.getOrDefault("autoDeploy", "false")
    String targetCluster   = pipeargs.getOrDefault("targetCluster", "dev01-us-west-2")
    pipeline{
        agent{
            label "swarm"
        }
        environment {
            GIT_SHA = sh(script: 'git rev-parse --short HEAD' , returnStdout: true).trim()
            AUTHOR  = sh(script: 'git show -s --pretty=\'%an <%ae>\'', returnStdout: true).trim()
            ONELINE = sh(script: 'git show -s --oneline', returnStdout: true).trim()
            DOCKER_BUILD_TAG = BUILD_TAG.replaceAll('%2F', '-')  // replace urlencoded slash with a dash
            SHA_TAG = "${GIT_BRANCH}-${GIT_SHA}"
        }

        stages{
            stage('ECR Docker login'){
                steps {
                    sh "aws ecr get-login-password --region us-west-2 | \
                        docker login --username AWS --password-stdin ${ecrRegistry}"
                }
            }
            stage('Build app image from dockerfile'){
            // https://www.jenkins.io/doc/book/pipeline/docker/
                steps {
                    script {
                        docker.withRegistry("https://${ecrRegistry}") {
                            app_image = docker.build("${dockerImageName}", "-f ${dockerfilePath} .")
                        }
                    }
                }
            }
            stage('Push app image to ECR') {
                steps {
                    echo "Push the Docker image to ECR..."
                    script {
                        docker.withRegistry("https://${ecrRegistry}"){
                            app_image.push(SHA_TAG)
                            app_image.push(DOCKER_BUILD_TAG)  // jenkins-<project>-<branch>-<build number>
                        }
                        // Cleanup: remove created tags and images from local after push
                        sh "docker rmi ${ecrRegistry}/${dockerImageName}:${SHA_TAG}"
                        sh "docker rmi ${ecrRegistry}/${dockerImageName}:${DOCKER_BUILD_TAG}"
                        sh "docker rmi ${dockerImageName}:latest"
                    }
                }
            }
            stage('Commit image tag if on polaris-dev integration branch') {
              when {
                allOf {
                  expression {
                    autoDeploy == "true"
                  }
                  anyOf {
                    branch 'develop'
                    branch 'dev'
                    branch 'titan_dev'
                  }
                }
              }
              steps {
                checkout([$class: 'GitSCM',
                    branches: [[name: '*/master']],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [
                      [$class: 'RelativeTargetDirectory',relativeTargetDir: 'polaris-workload-charts'],
                      [$class: 'LocalBranch', localBranch: 'master']
                    ],
                    submoduleCfg: [],
                    userRemoteConfigs: [[
                      credentialsId: 'github-infrrd-bot',
                      url: 'https://github.com/infer-ai/polaris-workload-charts.git'
                    ]]
                ])
                echo "Commit the image tag to the deployed values file, triggering Argo auto-sync"
                withCredentials([usernamePassword(
                  credentialsId: 'github-infrrd-bot', usernameVariable: 'USERNAME', passwordVariable: 'TOKEN'
                )]) {
                  script {
                    VALUES_FILE = "polaris-workload-charts/microservice-chart/deployed-values/dev/${targetCluster}/values.${dockerImageName}.yaml"
                    PREVIOUS_TAG = sh(returnStdout: true, script: "grep tag: ${VALUES_FILE} | cut -d':' -f2").trim()
                    sh """
                    sed -i.tmp 's/${PREVIOUS_TAG}/"${SHA_TAG}"/' ${VALUES_FILE}
                    cd polaris-workload-charts/
                    git commit -am "BOT update: ${dockerImageName}:${SHA_TAG}"
                    git show
                    git push https://${USERNAME}:${TOKEN}@github.com/infer-ai/polaris-workload-charts.git
                    """
                  }
                }
              }
            }
            stage('Commit image tag if on polaris-qa integration branch') {
              when {
                allOf {
                  expression {
                    autoDeploy == "true"
                  }
                  anyOf {
                    branch 'qa'
                  }
                }
              }
              steps {
                checkout([$class: 'GitSCM',
                    branches: [[name: '*/master']],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [
                      [$class: 'RelativeTargetDirectory',relativeTargetDir: 'polaris-workload-charts'],
                      [$class: 'LocalBranch', localBranch: 'master']
                    ],
                    submoduleCfg: [],
                    userRemoteConfigs: [[
                      credentialsId: 'github-infrrd-bot',
                      url: 'https://github.com/infer-ai/polaris-workload-charts.git'
                    ]]
                ])
                echo "Commit the image tag to the deployed values file, triggering Argo auto-sync"
                withCredentials([usernamePassword(
                  credentialsId: 'github-infrrd-bot', usernameVariable: 'USERNAME', passwordVariable: 'TOKEN'
                )]) {
                  script {
                    VALUES_FILE = "polaris-workload-charts/microservice-chart/deployed-values/qa/qa01-us-west-2/values.${dockerImageName}.yaml"
                    PREVIOUS_TAG = sh(returnStdout: true, script: "grep tag: ${VALUES_FILE} | cut -d':' -f2").trim()
                    sh """
                    sed -i.tmp 's/${PREVIOUS_TAG}/"${SHA_TAG}"/' ${VALUES_FILE}
                    cd polaris-workload-charts/
                    git commit -am "BOT update: ${dockerImageName}:${SHA_TAG}"
                    git show
                    git push https://${USERNAME}:${TOKEN}@github.com/infer-ai/polaris-workload-charts.git
                    """
                  }
                }
              }
            }
        }
        post{
            success{
                slackSend(
                    channel: "${slackChannel}",
                    attachments: [
                        [
                            color: "#18be52",
                            mrkdwn_in: ["text", "footer"],
                            text: "*Job*: ${env.JOB_NAME}\n*Commit*: ${ONELINE}\n*Author*: ${AUTHOR}\nBuilt and pushed `${dockerImageName}:${SHA_TAG}`",
                            footer: "<${env.BUILD_URL}|view build #${env.BUILD_NUMBER} in Jenkins>"
                        ]
                    ]
                )
                script {
                    currentBuild.description = "${dockerImageName}:${SHA_TAG}"
                }
            }
            failure{
                slackSend(
                    channel: "${slackChannel}",
                    attachments: [
                        [
                            color: "#f4c030",
                            mrkdwn_in: ["text", "footer"],
                            text: "*Job*: ${env.JOB_NAME}\n*Commit*: ${ONELINE}\n*Author*: ${AUTHOR}\nFAILED to build artifact.",
                            footer: "<${env.BUILD_URL}|view build #${env.BUILD_NUMBER} in Jenkins>"
                        ]
                    ]
                )
            }
        }
    }
}
