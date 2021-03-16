pipeline {
    agent { label 'docker' }
    stages {
        stage('Build') {
            steps {
                sh "docker build --tag ${GIT_COMMIT} ."
            }
        }
        stage('Publish latest') {
            when { branch 'master' }
            steps {
                sh "docker tag ${GIT_COMMIT} fintlabsacr.azurecr.io/link-walker:latest"
                withDockerRegistry([credentialsId: 'fintlabsacr.azurecr.io', url: 'https://fintlabsacr.azurecr.io']) {
                    sh "docker push fintlabsacr.azurecr.io/link-walker:latest"
                }
                sh "docker tag ${GIT_COMMIT} fint/link-walker:latest"
                withDockerRegistry([credentialsId: 'asgeir-docker', url: '']) {
                    sh "docker push fint/link-walker:latest"
                }
            }
        }
    }
}
