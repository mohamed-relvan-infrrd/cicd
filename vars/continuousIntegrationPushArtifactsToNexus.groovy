def call(Map<String,String> pipeargs) {
    String appExecutableJar = pipeargs.appExecutableJar
    String appName          = pipeargs.getOrDefault('appName', "")
    String mavenTag         = pipeargs.mavenTag
    String projectName      = pipeargs.projectName
    String ecrRegistry      = pipeargs.getOrDefault('ecrRegistryOverride', "592754518446.dkr.ecr.us-west-2.amazonaws.com" )
    String slackChannel     = pipeargs.getOrDefault("slackChannelOverride", "#polaris-cicd")
    String nexusVersion     = pipeargs.getOrDefault('nexusVersion', "nexus3")
    String nexusProtocal    = pipeargs.getOrDefault('nexusProtocal', "https")
    String nexusURL         = pipeargs.getOrDefault('nexusURL', "nexus.infrrdapis.com")
    String nexusRepository  = pipeargs.getOrDefault('nexusRepository', "titan-maven/")
    String artifactsExtension = pipeargs.getOrDefault('artifactsExtension', "jar")
    pipeline{
        agent{
            label "swarm"
        }
        environment {
            AUTHOR = sh(script: 'git show -s --pretty=\'%an <%ae>\'', returnStdout: true).trim()
            ONELINE =sh(script: 'git show -s --oneline', returnStdout: true).trim()
            GIT_SHA = sh ( script: 'git rev-parse --short HEAD' , returnStdout: true).trim()  // sh outputs a string
            DOCKER_BUILD_TAG = BUILD_TAG.replaceAll('%2F', '-')  // replace urlencoded slash with a dash
            SHA_TAG = "${GIT_BRANCH}-${GIT_SHA}"
            MAVEN_CONFIG = "${WORKSPACE}/m2"
            NEXUS_CREDENTIAL_ID = "nexus"
        }
        stages{
            stage('MavenBuild') {
                agent {
                    docker {
                        image "builder-maven-java:${mavenTag}"
                        registryUrl "https://${ecrRegistry}"
                        reuseNode true
                    }
                }
                steps {
                    withCredentials([usernamePassword(credentialsId: 'nexus',
                                     usernameVariable: 'NEXUS_USER',
                                     passwordVariable: 'NEXUS_PASSWORD')]){
                        withSonarQubeEnv('Polaris-Sonar') {
                            sh "mvn -B \
                                -f ./${appName} \
                                -s ${MAVEN_CONFIG}/settings-nexus.xml \
                                -Dmaven.repo.local=${MAVEN_CONFIG}/repository \
                                clean install sonar:sonar -DskipTests -Dsonar.projectName=${projectName}"
                        }
                    }
                }
            }
            stage("PushArtifactorytoNexus") {
                steps {
                    withCredentials([usernamePassword(credentialsId: 'nexus', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
                        script {
                            prefix = appName ? "${appName}/" : ""
                            pom = readMavenPom file: "${WORKSPACE}/${prefix}pom.xml";
                            artifactPath = "${WORKSPACE}/${prefix}target/${appExecutableJar}.${artifactsExtension}";
                            artifactExists = fileExists artifactPath;
                            if(artifactExists) {
                                echo "*** File: ${artifactPath}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}";
                                nexusArtifactUploader(
                                    nexusVersion: "${nexusVersion}",
                                    protocol: "${nexusProtocal}",
                                    nexusUrl: "${nexusURL}",
                                    groupId: pom.groupId,
                                    version: pom.version,
                                    repository: "${nexusRepository}",
                                    credentialsId: "${NEXUS_CREDENTIAL_ID}",
                                    artifacts: [
                                        [artifactId: pom.artifactId,
                                        classifier: '',
                                        file: "${artifactPath}",
                                        type: "${artifactsExtension}"]
                                    ]
                                );
                            } else {
                                error "*** File: ${artifactPath}, could not be found";
                            }
                        }
                    }
                }
            }            
        }
        post{
            always{
                script {
                    prefix = appName ? "${appName}/" : ""
                    archiveArtifacts artifacts: "${prefix}target/*.jar", fingerprint: true
                    // will fail if no test results found. Enable after we start running tests
                    // junit "${prefix}/target/surefire-reports/*.xml"
                }
            }
            success{
                slackSend(
                    channel: "${slackChannel}",
                    attachments: [
                        [
                            color: "#18be52",
                            mrkdwn_in: ["text", "footer"],
                            text: "*Job*: ${env.JOB_NAME}\n*Commit*: ${ONELINE}\n*Author*: ${AUTHOR}\nBuilt and pushed `${projectName}:${SHA_TAG}`",
                            footer: "<${env.BUILD_URL}|view build #${env.BUILD_NUMBER} in Jenkins>"
                        ]
                    ]
                )
                script {
                    currentBuild.description = "${projectName}:${SHA_TAG}"
                }
            }
            failure{
                slackSend(
                    channel: "${slackChannel}",
                    attachments: [
                        [
                            color: "#f4c030",
                            mrkdwn_in: ["text", "footer"],
                            text: "*Job*: ${env.JOB_NAME}\n*Commit*: ${ONELINE}\n*Author*: ${AUTHOR}\nFAILED to Build & Push artifact.",
                            footer: "<${env.BUILD_URL}|view build #${env.BUILD_NUMBER} in Jenkins>"
                        ]
                    ]
                )
            }
        }
    }
}
