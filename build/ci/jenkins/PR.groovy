#!/usr/bin/env groovy

int total_timeout_minutes = 120
int e2e_timeout_seconds = 50 * 60

pipeline {
    options {
        timestamps()
        timeout(time: total_timeout_minutes, unit: 'MINUTES')
        buildDiscarder logRotator(artifactDaysToKeepStr: '30')
        parallelsAlwaysFailFast()

    }
    agent {
            kubernetes {
                label 'milvus-e2e-test-pr' 
                inheritFrom 'default'
                defaultContainer 'main'
                yamlFile 'build/ci/jenkins/pod/rte.yaml'
                customWorkspace '/home/jenkins/agent/workspace'
                idleMinutes 120
            }
    }
    environment {
        PROJECT_NAME = "milvus"
        SEMVER = "${BRANCH_NAME.contains('/') ? BRANCH_NAME.substring(BRANCH_NAME.lastIndexOf('/') + 1) : BRANCH_NAME}"
        IMAGE_REPO = 'dockerhub-mirror-sh.zilliz.cc/milvusdb'
        DOCKER_BUILDKIT = 1
        ARTIFACTS = "${env.WORKSPACE}/_artifacts"
        DOCKER_CREDENTIALS_ID = "f0aacc8e-33f2-458a-ba9e-2c44f431b4d2"
        TARGET_REPO = "milvusdb"
        CI_DOCKER_CREDENTIAL_ID = "ci-docker-registry"
        MILVUS_HELM_NAMESPACE = "milvus-ci"
        DISABLE_KIND = true
        HUB = 'registry.milvus.io/milvus'
        JENKINS_BUILD_ID = "${env.BUILD_ID}"
    }

    stages {
        stage ('Build'){
            steps {
                container('main') {
                    dir ('tests/scripts') {
                        script {
                            sh 'printenv'
                            def date = sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                            def gitShortCommit = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()                           
                            withCredentials([usernamePassword(credentialsId: "${env.CI_DOCKER_CREDENTIAL_ID}", usernameVariable: 'CI_REGISTRY_USERNAME', passwordVariable: 'CI_REGISTRY_PASSWORD')]){
                                sh """
                                TAG="${env.BRANCH_NAME}-${date}-${gitShortCommit}" \
                                ./e2e-k8s.sh \
                                --skip-export-logs \
                                --skip-install \
                                --skip-cleanup \
                                --skip-setup \
                                --skip-test
                                """

                            }
                        }
                    }
                }
            }
        }


        stage ('Install & E2E Test') {
                matrix {
                    axes {
                        axis {
                            name 'MILVUS_SERVER_TYPE'
                            values 'standalone', 'distributed'
                        }
                        axis {
                            name 'MILVUS_CLIENT'
                            values 'pymilvus'
                        }
                    }

                stages {
                        stage('Install') {
                            steps {
                                container('main') {
                                    dir ('tests/scripts') {
                                        script {
                                            sh 'printenv'
                                            def clusterEnabled = "false"
                                            def setMemoryResourceLimitArgs="--set standalone.resources.limits.memory=4Gi"
                                            if ("${MILVUS_SERVER_TYPE}" == 'distributed') {
                                                clusterEnabled = "true"
                                                setMemoryResourceLimitArgs="--set queryNode.resources.limits.memory=4Gi"
                                            }

                                            def date = sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                                            def gitShortCommit = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                                            if ("${MILVUS_CLIENT}" == "pymilvus") {
                                                withCredentials([usernamePassword(credentialsId: "${env.CI_DOCKER_CREDENTIAL_ID}", usernameVariable: 'CI_REGISTRY_USERNAME', passwordVariable: 'CI_REGISTRY_PASSWORD')]){
                                                    sh """
                                                    MILVUS_CLUSTER_ENABLED=${clusterEnabled} \
                                                    TAG="${env.BRANCH_NAME}-${date}-${gitShortCommit}" \
                                                    ./e2e-k8s.sh \
                                                    --skip-export-logs \
                                                    --skip-cleanup \
                                                    --skip-setup \
                                                    --skip-test \
                                                    --skip-build \
                                                    --skip-build-image \
                                                    --install-extra-arg "--set etcd.persistence.storageClass=local-path ${setMemoryResourceLimitArgs} \
                                                    --set metrics.serviceMonitor.enabled=true" 
                                                    """
                                                }
                                            } else {
                                                error "Error: Unsupported Milvus client: ${MILVUS_CLIENT}"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    stage('E2E Test'){
                            steps {
                                container('pytest') {
                                    dir ('tests/scripts') {
                                        script {
                                            def release_name=sh(returnStdout: true, script: './get_release_name.sh')
                                            def clusterEnabled = 'false'
                                            if ("${MILVUS_SERVER_TYPE}" == "distributed") {
                                                clusterEnabled = "true"
                                            }
                                            if ("${MILVUS_CLIENT}" == "pymilvus") {
                                                sh """
                                                MILVUS_HELM_RELEASE_NAME="${release_name}" \
                                                MILVUS_CLUSTER_ENABLED="${clusterEnabled}" \
                                                ./e2e-k8s.sh \
                                                --skip-export-logs \
                                                --skip-cleanup \
                                                --skip-setup \
                                                --skip-build \
                                                --skip-build-image \
                                                --skip-install \
                                                --test-extra-arg "-x --tags L0 L1" \
                                                --test-timeout ${e2e_timeout_seconds}
                                                """
                                            } else {
                                            error "Error: Unsupported Milvus client: ${MILVUS_CLIENT}"
                                            }
                                        }
                                    }
                                }
                            }

                    }
                }
                post {
                    always {
                        container('pytest') {
                            dir ('tests/scripts') {
                            script {
                                    def release_name = sh(returnStdout: true, script: './get_release_name.sh ')
                                    sh "./ci_logs.sh --log-dir /ci-logs  --artifacts-name ${env.ARTIFACTS}/artifacts-${PROJECT_NAME}-${MILVUS_SERVER_TYPE}-${SEMVER}-${env.BUILD_NUMBER}-${MILVUS_CLIENT}-e2e-logs \
                                    --release-name ${release_name}"
                                    dir("${env.ARTIFACTS}") {
                                        if ("${MILVUS_CLIENT}" == "pymilvus") {
                                            sh "tar -zcvf artifacts-${PROJECT_NAME}-${MILVUS_SERVER_TYPE}-${MILVUS_CLIENT}-pytest-logs.tar.gz /tmp/ci_logs/test --remove-files || true"
                                            }
                                        archiveArtifacts artifacts: "**.tar.gz", allowEmptyArchive: true
                                    }
                            }
                            }
                        }
                    }
                    unsuccessful {
                        container('jnlp') {
                            script {
                                def authorEmail = sh returnStdout: true, script: 'git --no-pager show -s --format=\'%ae\' HEAD'
                                emailext subject: '$DEFAULT_SUBJECT',
                                body: '$DEFAULT_CONTENT',
                                recipientProviders: [developers(), culprits()],
                                replyTo: '$DEFAULT_REPLYTO',
                                to: "${authorEmail}"
                            }
                        }
                    }

                    // clean up when successful
                    cleanup{
                        container('main') {
                            dir ('tests/scripts') {  
                                script {
                                    def release_name=sh(returnStdout: true, script: './get_release_name.sh')
                                    sh "./uninstall_milvus.sh --release-name ${release_name}"
                                }
                            }
                        }
                    }
                }
                }
        }
    }
}
