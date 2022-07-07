def call(Map<String,String> pipeargs) {
    String dockerImageName = pipeargs.dockerImageName
    String dockerfilePath  = pipeargs.getOrDefault("dockerfilePath", "./Dockerfile")
    String ecrRegistry     = pipeargs.getOrDefault("ecrRegistryOverride", "592754518446.dkr.ecr.us-west-2.amazonaws.com" )
    String slackChannel    = pipeargs.getOrDefault("slackChannelOverride", "#polaris-cicd")
    String autoDeploy      = pipeargs.getOrDefault("autoDeploy", "false")
    String targetCluster   = pipeargs.getOrDefault("targetCluster", "dev01-us-west-2")
    // portal-ui build-args
    string nodeTag        = pipeargs.nodeTag
    string nginxTag        = pipeargs.nginxTag
    String profile         = pipeargs.getOrDefault("profile", "")
    String apiBaseUrl      = pipeargs.getOrDefault("apiBaseUrl", "")
    String tusBaseUrl      = pipeargs.getOrDefault("tusBaseUrl", "")
    pipeline{
        agent{
            label "swarm"
        }
        environment {
            GIT_SHA = sh(script: 'git rev-parse --short HEAD' , returnStdout: true).trim()
            AUTHOR  = sh(script: 'git show -s --pretty=\'%an <%ae>\'', returnStdout: true).trim()
            ONELINE = sh(script: 'git show -s --oneline', returnStdout: true).trim()
            DOCKER_BUILD_TAG = BUILD_TAG.replaceAll('%2F', '-')  // replace urlencoded slash with a dash
            ECR_REGISTRY = "${ecrRegistry}"
            SHA_TAG = "${GIT_BRANCH_TRY}-${GIT_SHA}"
            API_BASE_URL = "${apiBaseUrl}"
            TUS_BASE_URL = "${tusBaseUrl}"
            PROFILE = "${profile}"
            NODE_TAG = "${nodeTag}"
            NGINX_TAG = "${nginxTag}"
            
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
                        
                        // we set BRANCH_NAME to make when { branch } syntax work without multibranch job ( Commit Image tag step )
                        def commit = checkout scm                        
                        env.BRANCH_NAME = commit.GIT_BRANCH.replace('origin/', '')
                                                
                        
                        docker.withRegistry("https://${ecrRegistry}") {
                            app_image = docker.build("${dockerImageName}",
                            "-f ${dockerfilePath} \
                            --build-arg NODE_TAG \
                            --build-arg NGINX_TAG \
                            --build-arg PROFILE \
                            --build-arg API_BASE_URL \
                            --build-arg TUS_BASE_URL \
                            --build-arg ECR_REGISTRY \
                            --build-arg GIT_COMMIT \
                            .")
                        }
                    }
                    echo "GIT_BRANCH_TRY : " +env.GIT_BRANCH_TRY
                    echo "SHA_TAG:  " +env.SHA_TAG
                    echo "GIT_BRANCH: " +env.GIT_BRANCH 
                    echo "BRANCH_NAME: " +env.BRANCH_NAME                    
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
                        sh "docker image rm ${ecrRegistry}/${dockerImageName}:${SHA_TAG}"
                    }
                }
            }
            stage('Commit image tag to Polaris-dev env if on integration branch develop') {
              when {
                allOf {
                  expression {
                    autoDeploy == "true"
                  }
                  anyOf {
                    branch 'develop'
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
            stage('Commit image tag to Polaris-QA env if on integration branch qa') {
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
                    currentBuild.description = """IMAGE: ${dockerImageName}:${SHA_TAG}
TAG: $SHA_TAG
TAG: $DOCKER_BUILD_TAG
API_BASE_URL: $API_BASE_URL
TUS_BASE_URL: $TUS_BASE_URL
PROFILE: $PROFILE"""
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
