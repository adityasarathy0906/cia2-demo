
pipeline {
    agent any

    stages {
        stage('Checkout Git') {
            steps {
                checkout scm
            }
        }

        stage('Check Java') {
            steps {
                bat 'java -version'
            }
        }

        stage('Build and Test with Maven') {
            steps {
                bat 'mvnw.cmd -B clean verify'
            }
        }

        stage('Check Docker') {
            steps {
                bat '"C:\\Users\\acer\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe" --version'
                bat '"C:\\Users\\acer\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe" info'
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Application built, tests passed, and Docker is accessible!'
        }

        failure {
            echo 'FAILED: Check the console output for the exact error.'
        }

        always {
            echo 'Jenkins CI pipeline finished.'
        }
    }
}
