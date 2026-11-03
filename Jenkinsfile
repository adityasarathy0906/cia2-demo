
pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }

    environment {
        ACR_NAME = 'adityacia2acr'
        ACR_LOGIN_SERVER = 'adityacia2acr.azurecr.io'
        IMAGE_NAME = 'cia2-demo'
        RESOURCE_GROUP = 'cia2-rg'
        AKS_CLUSTER = 'cia2-aks'
    }

    stages {
        stage('Checkout Git') {
            steps {
                checkout scm
            }
        }

        stage('Build and Test with Maven') {
            steps {
                sh 'mvn -B clean verify'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    docker build -t ${ACR_LOGIN_SERVER}/${IMAGE_NAME}:${BUILD_NUMBER} .
                    docker tag ${ACR_LOGIN_SERVER}/${IMAGE_NAME}:${BUILD_NUMBER} ${ACR_LOGIN_SERVER}/${IMAGE_NAME}:latest
                '''
            }
        }

        stage('Login to Azure') {
            steps {
                withCredentials([azureServicePrincipal('azure-credentials')]) {
                    sh '''
                        az login --service-principal \
                          --username "$AZURE_CLIENT_ID" \
                          --password "$AZURE_CLIENT_SECRET" \
                          --tenant "$AZURE_TENANT_ID"

                        az account set --subscription "$AZURE_SUBSCRIPTION_ID"
                    '''
                }
            }
        }

        stage('Push Image to ACR') {
            steps {
                sh '''
                    az acr login --name ${ACR_NAME}
                    docker push ${ACR_LOGIN_SERVER}/${IMAGE_NAME}:${BUILD_NUMBER}
                    docker push ${ACR_LOGIN_SERVER}/${IMAGE_NAME}:latest
                '''
            }
        }

        stage('Deploy to AKS') {
            steps {
                sh '''
                    az aks get-credentials \
                      --resource-group ${RESOURCE_GROUP} \
                      --name ${AKS_CLUSTER} \
                      --admin \
                      --overwrite-existing

                    kubectl set image deployment/cia2-demo \
                      cia2-demo=${ACR_LOGIN_SERVER}/${IMAGE_NAME}:${BUILD_NUMBER}

                    kubectl rollout status deployment/cia2-demo --timeout=180s
                '''
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Application deployed to Azure Kubernetes Service!'
        }
        failure {
            echo 'FAILED: Check the stage logs above to troubleshoot.'
        }
        always {
            echo 'Jenkins CI/CD pipeline finished.'
        }
    }
}
