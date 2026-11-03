
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
    }

    post {
        success {
            echo 'SUCCESS: Application built and tests passed!'
        }
        failure {
            echo 'FAILED: Check the console output.'
        }
        always {
            echo 'Jenkins CI pipeline finished.'
        }
    }
}
