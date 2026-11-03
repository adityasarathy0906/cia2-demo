
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        // Azure CLI
        AZ_CLI = 'C:\\Program Files\\Microsoft SDKs\\Azure\\CLI2\\wbin\\az.cmd'

        // Docker Desktop
        DOCKER_EXE = 'C:\\Users\\acer\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'
        DOCKER_DIR = 'C:\\Users\\acer\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin'

        // Kubernetes CLI
        KUBECTL_EXE = 'C:\\Users\\acer\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Kubernetes.kubectl_Microsoft.Winget.Source_8wekyb3d8bbwe\\kubectl.exe'

        // Azure configuration
        AZURE_TENANT_ID = '5beb351c-3fb8-418f-b612-fe36ace96ef3'
        AZURE_SUBSCRIPTION_ID = 'c093f10b-1f50-4616-80e8-614c8f827a79'

        // Azure Container Registry
        ACR_NAME = 'adityacia2acr'
        ACR_LOGIN_SERVER = 'adityacia2acr.azurecr.io'

        // Application
        IMAGE_NAME = 'cia2-demo'

        // AKS configuration
        AZURE_RESOURCE_GROUP = 'cia2-rg'
        AKS_NAME = 'cia2-aks'
        DEPLOYMENT_NAME = 'cia2-demo'
    }

    stages {

        // STAGE 1: GET SOURCE CODE FROM GITHUB
        stage('Checkout Git') {
            steps {
                echo 'Checking out source code from GitHub...'
                checkout scm
            }
        }

        // STAGE 2: CHECK JAVA
        stage('Check Java') {
            steps {
                echo 'Checking Java installation...'
                bat 'java -version'
            }
        }

        // STAGE 3: BUILD AND TEST WITH MAVEN
        stage('Build and Test with Maven') {
            steps {
                echo 'Building application and running tests...'
                bat 'call mvnw.cmd -B clean verify'
            }
        }

        // STAGE 4: VERIFY DOCKER
        stage('Check Docker') {
            steps {
                echo 'Checking Docker installation and engine...'

                bat '"%DOCKER_EXE%" --version'
                bat '"%DOCKER_EXE%" info'
            }
        }

        // STAGE 5: BUILD DOCKER IMAGE
        stage('Build Docker Image') {
            steps {
                script {
                    env.IMAGE_TAG = "build-${env.BUILD_NUMBER}"

                    env.IMAGE_URI =
                        "${env.ACR_LOGIN_SERVER}/${env.IMAGE_NAME}:${env.IMAGE_TAG}"

                    env.IMAGE_LATEST =
                        "${env.ACR_LOGIN_SERVER}/${env.IMAGE_NAME}:latest"
                }

                echo 'Building Docker image...'

                bat '''
                    "%DOCKER_EXE%" build ^
                    -t "%IMAGE_URI%" ^
                    -t "%IMAGE_LATEST%" .
                '''
            }
        }

        // STAGE 6: AZURE AUTHENTICATION, ACR PUSH AND AKS DEPLOYMENT
        stage('Azure Push and Kubernetes Deployment') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'azure-sp',
                        usernameVariable: 'AZURE_CLIENT_ID',
                        passwordVariable: 'AZURE_CLIENT_SECRET'
                    )
                ]) {

                    withEnv([
                        "KUBECONFIG=${env.WORKSPACE}\\.kube\\config"
                    ]) {

                        script {
                            try {

                                // AZURE LOGIN
                                echo 'Authenticating with Azure...'

                                bat """
                                    @echo off

                                    set "PATH=${env.DOCKER_DIR};%PATH%"

                                    "%AZ_CLI%" login ^
                                    --service-principal ^
                                    --username "%AZURE_CLIENT_ID%" ^
                                    --password "%AZURE_CLIENT_SECRET%" ^
                                    --tenant "%AZURE_TENANT_ID%" ^
                                    --output none

                                    if errorlevel 1 exit /b 1

                                    "%AZ_CLI%" account set ^
                                    --subscription "%AZURE_SUBSCRIPTION_ID%"

                                    if errorlevel 1 exit /b 1
                                """

                                // ACR LOGIN
                                echo 'Authenticating with Azure Container Registry...'

                                bat '''
                                    set "PATH=%DOCKER_DIR%;%PATH%"

                                    "%AZ_CLI%" acr login --name "%ACR_NAME%"

                                    if errorlevel 1 exit /b 1
                                '''

                                // PUSH DOCKER IMAGES
                                echo 'Pushing Docker images to Azure Container Registry...'

                                bat '''
                                    "%DOCKER_EXE%" push "%IMAGE_URI%"

                                    if errorlevel 1 exit /b 1

                                    "%DOCKER_EXE%" push "%IMAGE_LATEST%"

                                    if errorlevel 1 exit /b 1
                                '''

                                // GET AKS CREDENTIALS
                                echo 'Connecting to Azure Kubernetes Service...'

                                bat '''
                                    if not exist "%WORKSPACE%\\.kube" mkdir "%WORKSPACE%\\.kube"

                                    "%AZ_CLI%" aks get-credentials ^
                                    --resource-group "%AZURE_RESOURCE_GROUP%" ^
                                    --name "%AKS_NAME%" ^
                                    --file "%KUBECONFIG%" ^
                                    --overwrite-existing

                                    if errorlevel 1 exit /b 1

                                    "%KUBECTL_EXE%" get nodes

                                    if errorlevel 1 exit /b 1
                                '''

                                // FIND DEPLOYMENT CONTAINER
                                echo 'Identifying Kubernetes deployment container...'

                                def containerName = bat(
                                    returnStdout: true,
                                    script: """
                                        @echo off
                                        "%KUBECTL_EXE%" get deployment "%DEPLOYMENT_NAME%" -o "jsonpath={.spec.template.spec.containers[0].name}"
                                    """
                                ).trim()

                                if (!containerName) {
                                    error('Could not identify the Kubernetes container name.')
                                }

                                echo "Container identified: ${containerName}"

                                // UPDATE KUBERNETES DEPLOYMENT
                                echo 'Deploying the new Docker image to AKS...'

                                bat """
                                    "%KUBECTL_EXE%" set image ^
                                    deployment/%DEPLOYMENT_NAME% ^
                                    "${containerName}=%IMAGE_URI%"

                                    if errorlevel 1 exit /b 1
                                """

                                // VERIFY ROLLOUT
                                echo 'Waiting for Kubernetes rollout...'

                                bat '''
                                    "%KUBECTL_EXE%" rollout status ^
                                    deployment/%DEPLOYMENT_NAME% ^
                                    --timeout=180s

                                    if errorlevel 1 exit /b 1
                                '''

                                // VERIFY FINAL DEPLOYMENT
                                echo 'Checking deployment status...'

                                bat '''
                                    "%KUBECTL_EXE%" get deployments

                                    "%KUBECTL_EXE%" get pods -o wide

                                    "%KUBECTL_EXE%" get services
                                '''

                                echo 'Azure deployment completed successfully!'

                            } finally {

                                // CLEAN UP AUTHENTICATION AND TEMP KUBECONFIG
                                echo 'Cleaning up temporary credentials...'

                                bat(
                                    returnStatus: true,
                                    script: '"%DOCKER_EXE%" logout "%ACR_LOGIN_SERVER%"'
                                )

                                bat(
                                    returnStatus: true,
                                    script: '"%AZ_CLI%" logout'
                                )

                                bat(
                                    returnStatus: true,
                                    script: 'if exist "%KUBECONFIG%" del /f /q "%KUBECONFIG%"'
                                )

                                bat(
                                    returnStatus: true,
                                    script: 'if exist "%WORKSPACE%\\.kube" rmdir /s /q "%WORKSPACE%\\.kube"'
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // FINAL PIPELINE STATUS
    post {
        success {
            echo '''
            ==========================================
                 CIA2 CI/CD PIPELINE SUCCESSFUL
            ==========================================
                 GitHub Checkout       : SUCCESS
                 Maven Build & Tests   : SUCCESS
                 Docker Build          : SUCCESS
                 Azure Authentication : SUCCESS
                 ACR Image Push        : SUCCESS
                 Kubernetes Deployment: SUCCESS
            ==========================================
                 APPLICATION DEPLOYED ON AZURE AKS
            ==========================================
            '''
        }

        failure {
            echo '''
            ==========================================
                 CIA2 CI/CD PIPELINE FAILED
            ==========================================
                 Check the Jenkins Console Output
                 to identify the failed stage.
            ==========================================
            '''
        }

        always {
            echo 'Jenkins CI/CD pipeline execution finished.'
        }
    }
}
