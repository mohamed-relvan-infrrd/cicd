def call(Map<String,String> pipeargs) {
    String mysqlHost = pipeargs.getOrDefault("mysqlHost", "polaris-dev01-us-west-2-mysql-80.c8fwj0b2wkn5.us-west-2.rds.amazonaws.com")
    String pgHost = pipeargs.getOrDefault("pgHost", "polaris-dev01-us-west-2-postgres.c8fwj0b2wkn5.us-west-2.rds.amazonaws.com")

    // CredentialStore ID
    String gitCredentials = pipeargs.getOrDefault("gitCredentials", "github-infrrd-bot")
    String mysqlCredentials = pipeargs.getOrDefault("mysqlCredentials", "mysql")
    String postgresCredentials = pipeargs.getOrDefault("postgresCredentials", "postgres")

    // Branch/tag/sha tune-able for each db repo
    String branch = pipeargs.getOrDefault("branch", "master")
    String APIGatewayBranch = pipeargs.getOrDefault("APIGatewayBranch", "titan_dev")
    String modelManagementBranch = pipeargs.getOrDefault("modelManagementBranch", "master")

    pipeline{
        agent{
            label "swarm"
        }
        parameters {
            choice(name: 'db', choices: [
                'NONE',
                'ALL',
                'api-gateway',
                'data-correction',
                'iam',
                'kms',
                'notification',
                'portal',
                'session',
            ],
            description: 'Which database to bootstrap')
            choice(name: 'RunSQL', choices: [
                'false',
                'true',
            ],
            description: 'Run sql commands against the database(s)')
        }
        environment {
            MYSQL_DATABASE_SQL="${WORKSPACE}/mysql_database.sql"
            POSTGRES_DATABASE_SQL="${WORKSPACE}/postgres_database.sql"
            MYSQL_HOST="${mysqlHost}"
            PGHOST="${pgHost}"
            RUN_SQL= "${RunSQL}"
        }
        stages {
            stage('Cloning db-schema repo') {
                steps {
                    echo "check out from [${branch}] branch"
                    checkout ([
                        $class: 'GitSCM',
                        branches: [[name: branch ]],
                        userRemoteConfigs: [[
                            credentialsId: gitCredentials,
                            url: 'https://github.com/infer-ai/titan-db-schema.git'
                        ]]
                    ])
                }
            }
            stage("Postgres") {
                when {
                    anyOf {
                        expression { db == "ALL" }
                        expression { db == "session" }
                    }
                }
                stages {
                    stage("Initialize database file") {
                        steps {
                            sh "echo -n '' > ${POSTGRES_DATABASE_SQL}"
                        }
                    }
                    stage('initialing session db') {
                        when {
                            anyOf {
                                expression { db == "ALL" }
                                expression { db == "session" }
                            }
                        }
                        steps {
                            sh '''
                            cd postgres/session
                            cat postgres-session-db.sql >> ${POSTGRES_DATABASE_SQL}
                            '''
                        }
                    }
                    // run in mysql docker container.
                    stage("running db scripts against RDS postgres") {
                        agent {
                            docker {
                                image "postgres:14"
                                reuseNode true
                            }
                        }
                        steps {
                            withCredentials([
                                    usernamePassword(
                                        credentialsId: postgresCredentials,
                                        usernameVariable: 'PGUSER',
                                        passwordVariable: 'PGPASSWORD'
                                        )
                            ]){
                                sh '''
                                cat -n ${POSTGRES_DATABASE_SQL}
                                if [ "${RUN_SQL}" = "true" ]; then
                                    psql < ${POSTGRES_DATABASE_SQL}
                                fi
                                '''
                            }
                        }
                    }
                }
            }
            stage("Mysql") {
                when {
                    anyOf {
                        expression { db == "ALL" }
                        expression { db == "data-correction" }
                        expression { db == "iam" }
                        expression { db == "kms" }
                        expression { db == "notification" }
                        expression { db == "portal" }
                    }
                }
                stages {
                    stage("Initialize database file") {
                        steps {
                            sh "echo -n '' > ${MYSQL_DATABASE_SQL}"
                        }
                    }
                    stage('loading IAM') {
                        when {
                            anyOf {
                                expression { db == "ALL" }
                                expression { db == "iam" }
                            }
                        }
                        steps {
                            sh '''
                            cd mysql/iam-db
                            cat iam.sql >> ${MYSQL_DATABASE_SQL}
                            cat seed.sql >> ${MYSQL_DATABASE_SQL}
                            cat [0-9][0-9][0-9]_*.sql >> ${MYSQL_DATABASE_SQL}
                            '''
                        }
                    }
                    stage('loading Data Correction') {
                        when {
                            anyOf {
                                expression { db == "ALL" }
                                expression { db == "data-correction" }
                            }
                        }
                        steps {
                            sh '''
                            cd mysql/data-correction
                            cat dc.sql dc_v2.sql >> ${MYSQL_DATABASE_SQL}
                            cat [0-9][0-9][0-9]_*.sql >> ${MYSQL_DATABASE_SQL}
                            '''
                        }
                    }
                    // KMS updates have a dependency on data_correction.
                    stage('loading KMS') {
                        when {
                            anyOf {
                                expression { db == "ALL" }
                                expression { db == "kms" }
                            }
                        }
                        steps {
                            sh '''
                            cd mysql/kms-db
                            cat kms.sql >> ${MYSQL_DATABASE_SQL}
                            cat [0-9][0-9][0-9]_*.sql >> ${MYSQL_DATABASE_SQL}
                            '''
                        }
                    }
                    stage('loading Notification') {
                        when {
                            anyOf {
                                expression { db == "ALL" }
                                expression { db == "notification" }
                            }
                        }
                        steps {
                            sh '''
                            cd mysql/notification-db
                            cat notification.sql >> ${MYSQL_DATABASE_SQL}
                            # cat [0-9][0-9][0-9]_*.sql >> ${MYSQL_DATABASE_SQL}
                            '''
                        }
                    }
                    stage('Cloning API-gateway') {
                        when {
                            anyOf {
                                expression { db == "ALL" }
                                expression { db == "api-gateway" }
                            }
                        }
                        steps {
                            echo "check out from [${APIGatewayBranch}] branch"
                            git (
                                    url: 'https://github.com/infer-ai/gimlet-api-gateway.git',
                                    credentialsId: gitCredentials,
                                    branch: "${APIGatewayBranch}"
                                )
                            sh '''
                            cd api-gateway/src/main/resources/dbscripts
                            cat apigateway_db.sql >> ${MYSQL_DATABASE_SQL}
                            # cat [0-9][0-9][0-9]_*.sql >> ${MYSQL_DATABASE_SQL}
                            '''
                        }
                    }
                    stage('Cloning model management for portal_db') {
                        when {
                            anyOf {
                                expression { db == "ALL" }
                                expression { db == "portal" }
                            }
                        }
                        steps {
                            echo "check out from [${modelManagementBranch}] branch"
                            git (
                                    url: 'https://github.com/infer-ai/titans-model-management-b.git',
                                    credentialsId: gitCredentials,
                                    branch: "${modelManagementBranch}"
                                )
                            sh '''
                            cd "src/main/resources/DB Scripts"
                            cat mm.sql >> ${MYSQL_DATABASE_SQL}
                            # cat [0-9][0-9][0-9]_*.sql >> ${MYSQL_DATABASE_SQL}
                            '''
                        }
                    }
                    stage('Creating blank schemas') {
                        when {
                            anyOf {
                                expression { db == "ALL" }
                            }
                        }
                        steps {
                            // May need to manually bootstrap camunda if it continues to fail upgrading from blank slate
                            sh '''
                            echo "CREATE SCHEMA IF NOT EXISTS idc_platform_orchestrator_db;" >> ${MYSQL_DATABASE_SQL}
                            echo "CREATE SCHEMA IF NOT EXISTS data_correction;" >> ${MYSQL_DATABASE_SQL}
                            '''
                        }
                    }
                    // run in mysql docker container.
                    stage("running db scripts against the mysql pod") {
                        agent {
                            docker {
                                image "mysql:8"
                                reuseNode true
                            }
                        }
                        steps {
                            withCredentials([
                                    usernamePassword(
                                        credentialsId: mysqlCredentials,
                                        usernameVariable: 'MYSQL_USERNAME',
                                        passwordVariable: 'MYSQL_PASS'
                                        )
                            ]){
                                sh '''
                                cat -n ${MYSQL_DATABASE_SQL}
                                if [ "${RUN_SQL}" = "true" ]; then
                                    mysql --host=${MYSQL_HOST} -u ${MYSQL_USERNAME} -p${MYSQL_PASS} < ${MYSQL_DATABASE_SQL}
                                fi
                                '''
                            }
                        }
                    }
                }
            }
        }
        post{
            success{
                echo "send slack success message"
            }
            failure{
                echo "send slack fail message"
            }
            always{
                script {
                    currentBuild.description = """database: ${db}, run: ${RUN_SQL}

schema: ${branch}
gateway: ${APIGatewayBranch}
model-management: ${modelManagementBranch}
"""
                }
            }
        }
    }
}
