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
                sh "docker tag ${GIT_COMMIT} fintlabs.azurecr.io/link-walker:latest"
                withDockerRegistry([credentialsId: 'fintlabs.azurecr.io', url: 'https://fintlabs.azurecr.io']) {
                    sh "docker push fintlabs.azurecr.io/link-walker:latest"
                }
            }
        }
        stage('Publish dist') {
            when { branch 'dist' }
            steps {
                sh "docker tag ${GIT_COMMIT} fint/link-walker:latest"
                withDockerRegistry([credentialsId: 'asgeir-docker', url: 'https://hub.docker.com']) {
                    sh "docker push fint/link-walker:latest"
                }
            }
        }
        stage('Publish PR') {
            when { changeRequest() }
            steps {
                sh "docker tag ${GIT_COMMIT} fintlabs.azurecr.io/link-walker:${BRANCH_NAME}"
                withDockerRegistry([credentialsId: 'fintlabs.azurecr.io', url: 'https://fintlabs.azurecr.io']) {
                    sh "docker push fintlabs.azurecr.io/link-walker:${BRANCH_NAME}"
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
                sh "docker tag ${GIT_COMMIT} fintlabs.azurecr.io/link-walker:${VERSION}"
                withDockerRegistry([credentialsId: 'fintlabs.azurecr.io', url: 'https://fintlabs.azurecr.io']) {
                    sh "docker push fintlabs.azurecr.io/link-walker:${VERSION}"
                }
            }
        }
    }
}
