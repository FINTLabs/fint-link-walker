pipeline {
    agent { label 'docker' }
    stages {
        stage('Build') {
            steps {
                sh "docker build --tag ${GIT_COMMIT} ."
            }
        }
        stage('Publish master') {
            when { branch 'master' }
            steps {
                sh "docker tag ${GIT_COMMIT} fintlabsacr.azurecr.io/link-walker:build.${BUILD_NUMBER}"
                withDockerRegistry([credentialsId: 'fintlabsacr.azurecr.io', url: 'https://fintlabsacr.azurecr.io']) {
                    sh "docker push fintlabsacr.azurecr.io/link-walker:build.${BUILD_NUMBER}"
                }
                sh "docker tag ${GIT_COMMIT} fint/link-walker:latest"
                withDockerRegistry([credentialsId: 'asgeir-docker', url: '']) {
                    sh "docker push fint/link-walker:latest"
                }
            }
        }
        stage('Publish PR') {
            when { changeRequest() }
            steps {
                sh "docker tag ${GIT_COMMIT} fintlabsacr.azurecr.io/link-walker:${BRANCH_NAME}.${BUILD_NUMBER}"
                withDockerRegistry([credentialsId: 'fintlabsacr.azurecr.io', url: 'https://fintlabsacr.azurecr.io']) {
                    sh "docker push fintlabsacr.azurecr.io/link-walker:${BRANCH_NAME}.${BUILD_NUMBER}"
                }
            }
        }
        stage('Publish Version') {
            when {
                tag pattern: "v\\d+\\.\\d+\\.\\d+(-\\w+-\\d+)?", comparator: "REGEXP"
            }
            steps {
                script {
                    VERSION = TAG_NAME[1..-1]
                }
                sh "docker tag ${GIT_COMMIT} fintlabsacr.azurecr.io/link-walker:${VERSION}"
                withDockerRegistry([credentialsId: 'fintlabsacr.azurecr.io', url: 'https://fintlabsacr.azurecr.io']) {
                    sh "docker push fintlabsacr.azurecr.io/link-walker:${VERSION}"
                }
                sh "docker tag ${GIT_COMMIT} fint/link-walker:latest"
                sh "docker tag ${GIT_COMMIT} fint/link-walker:${VERSION}"
                withDockerRegistry([credentialsId: 'asgeir-docker', url: '']) {
                    sh "docker push fint/link-walker:latest"
                    sh "docker push fint/link-walker:${VERSION}"
                }
            }
        }
    }
}
