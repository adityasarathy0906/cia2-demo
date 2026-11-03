
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        // AZURE CLI
        AZ_CLI = 'C:\\Program Files\\Microsoft SDKs\\Azure\\CLI2\\wbin\\az.cmd'

        // DOCKER DESKTOP
        DOCKER_EXE = 'C:\\Users\\acer\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'

        // KUBERNETES CLI
        KUBECTL_EXE = 'C:\\Users\\acer\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Kubernetes.kubectl_Microsoft.Winget.Source_8wekyb3d8bbwe\\kubectl.exe'

        // AZURE CONFIGURATION
        AZURE_TENANT_ID = '5beb351c-3fb8-418f-b612-fe36ace96ef3'
        AZURE_SUBSCRIPTION_ID = 'c093f10b-1f50-4616-80e8-614c8f827a79'

        // AZURE CONTAINER REGISTRY
        ACR_NAME = 'adityacia2acr'
        ACR_LOGIN_SERVER = 'adityacia2acr.azurecr.io'

        // APPLICATION
        IMAGE_NAME = 'cia2-demo'

        // AZURE KUBERNETES SERVICE
        AZURE_RESOURCE_GROUP = 'cia2-rg'
        AKS_NAME = 'cia2-aks'
        DEPLOYMENT_NAME = 'cia2-demo'
    }

    stages {

        // STAGE 1: GIT CHECKOUT
        stage('Checkout Git') {
            steps {
                echo 'Checking out source code from GitHub...'
                checkout scm
            }
        }

        // STAGE 2: JAVA CHECK
        stage('Check Java') {
            steps {
                echo 'Checking Java installation...'
                bat 'java -version'
            }
        }

        // STAGE 3: MAVEN BUILD AND TEST
        stage('Build and Test with Maven') {
            steps {
                echo 'Building application and running tests...'
                bat 'call mvnw.cmd -B clean verify'
            }
        }

        // STAGE 4: DOCKER CHECK
        stage('Check Docker') {
            steps {
                echo 'Checking Docker installation...'
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

                echo "Building image: ${env.IMAGE_URI}"

                bat '''
                    @echo off

                    "%DOCKER_EXE%" build ^
                    -t "%IMAGE_URI%" ^
                    -t "%IMAGE_LATEST%" .

                    if errorlevel 1 exit /b 1
                '''
            }
        }

        // STAGE 6: AZURE AND KUBERNETES DEPLOYMENT
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
                        "KUBECONFIG=${env.WORKSPACE}\\.kube\\config",
                        "DOCKER_CONFIG=${env.WORKSPACE}\\.docker-ci",
                        "AZURE_CONFIG_DIR=${env.WORKSPACE}\\.azure-ci"
                    ]) {

                        script {
                            try {

                                // INITIALIZE DIRECTORIES
                                echo 'Preparing isolated configurations...'

                                bat '''
                                    @echo off

                                    if not exist "%DOCKER_CONFIG%" mkdir "%DOCKER_CONFIG%"

                                    if not exist "%AZURE_CONFIG_DIR%" mkdir "%AZURE_CONFIG_DIR%"

                                    if not exist "%WORKSPACE%\\.kube" mkdir "%WORKSPACE%\\.kube"

                                    > "%DOCKER_CONFIG%\\config.json" echo {}
                                '''

                                // DISABLE AZURE CLI TELEMETRY
                                writeFile(
                                    file: '.azure-ci/config',
                                    text: '[core]\\ncollect_telemetry=no\\n'
                                )

                                // AZURE AUTHENTICATION
                                echo 'Authenticating with Azure...'

                                bat '''
                                    @echo off

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
                                '''

                                echo 'Azure authentication successful!'

                                // ACR AUTHENTICATION USING EXPOSED TOKEN
                                echo 'Obtaining ACR access token...'

                                writeFile file: 'acr-login.ps1', text: '''
$ErrorActionPreference = "Stop"

$token = & $env:AZ_CLI acr login `
    --name $env:ACR_NAME `
    --expose-token `
    --query accessToken `
    --output tsv

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($token)) {
    Write-Error "Could not obtain ACR access token."
    exit 1
}

$token | & $env:DOCKER_EXE --config $env:DOCKER_CONFIG login `
    $env:ACR_LOGIN_SERVER `
    --username "00000000-0000-0000-0000-000000000000" `
    --password-stdin

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker registry login failed."
    exit 1
}

Write-Output "ACR authentication successful."
'''

                                bat '''
                                    @echo off

                                    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%WORKSPACE%\\acr-login.ps1"

                                    if errorlevel 1 exit /b 1
                                '''

                                echo 'ACR authentication successful!'

                                // PUSH VERSIONED IMAGE
                                echo 'Pushing versioned Docker image to ACR...'

                                bat '''
                                    @echo off

                                    "%DOCKER_EXE%" --config "%DOCKER_CONFIG%" push "%IMAGE_URI%"

                                    if errorlevel 1 exit /b 1
                                '''

                                // PUSH LATEST IMAGE
                                echo 'Pushing latest Docker image to ACR...'

                                bat '''
                                    @echo off

                                    "%DOCKER_EXE%" --config "%DOCKER_CONFIG%" push "%IMAGE_LATEST%"

                                    if errorlevel 1 exit /b 1
                                '''

                                echo 'Both Docker images pushed successfully!'

                                // GET AKS CREDENTIALS
                                echo 'Connecting to Azure Kubernetes Service...'

                                bat '''
                                    @echo off

                                    echo Kubeconfig path: %KUBECONFIG%

                                    if not exist "%WORKSPACE%\\.kube" mkdir "%WORKSPACE%\\.kube"

                                    "%AZ_CLI%" aks get-credentials ^
                                    --resource-group "%AZURE_RESOURCE_GROUP%" ^
                                    --name "%AKS_NAME%" ^
                                    --file "%KUBECONFIG%" ^
                                    --overwrite-existing

                                    if errorlevel 1 exit /b 1

                                    if not exist "%KUBECONFIG%" (
                                        echo ERROR: Kubeconfig was not created.
                                        exit /b 1
                                    )

                                    echo Kubeconfig successfully created.

                                    "%KUBECTL_EXE%" --kubeconfig "%KUBECONFIG%" get nodes

                                    if errorlevel 1 exit /b 1
                                '''

                                echo 'Successfully connected to AKS!'

                                // IDENTIFY CONTAINER NAME
                                echo 'Identifying Kubernetes container...'

                                def containerName = bat(
                                    returnStdout: true,
                                    script: '''
                                        @echo off

                                        "%KUBECTL_EXE%" --kubeconfig "%KUBECONFIG%" get deployment "%DEPLOYMENT_NAME%" -o "jsonpath={.spec.template.spec.containers[0].name}"
                                    '''
                                ).trim()

                                if (!containerName) {
                                    error('Unable to identify Kubernetes container.')
                                }

                                echo "Container identified: ${containerName}"

                                // UPDATE KUBERNETES DEPLOYMENT
                                echo 'Deploying new image to AKS...'

                                bat """
                                    @echo off

                                    "%KUBECTL_EXE%" --kubeconfig "%KUBECONFIG%" set image ^
                                    deployment/%DEPLOYMENT_NAME% ^
                                    "${containerName}=%IMAGE_URI%"

                                    if errorlevel 1 exit /b 1
                                """

                                echo 'Kubernetes deployment updated!'

                                // VERIFY ROLLOUT
                                echo 'Waiting for Kubernetes rollout...'

                                bat '''
                                    @echo off

                                    "%KUBECTL_EXE%" --kubeconfig "%KUBECONFIG%" rollout status ^
                                    deployment/%DEPLOYMENT_NAME% ^
                                    --timeout=180s

                                    if errorlevel 1 exit /b 1
                                '''

                                echo 'Kubernetes rollout successful!'

                                // FINAL VERIFICATION
                                echo 'Verifying deployment status...'

                                bat '''
                                    @echo off

                                    echo ===== DEPLOYMENTS =====

                                    "%KUBECTL_EXE%" --kubeconfig "%KUBECONFIG%" get deployments

                                    echo ===== PODS =====

                                    "%KUBECTL_EXE%" --kubeconfig "%KUBECONFIG%" get pods -o wide

                                    echo ===== SERVICES =====

                                    "%KUBECTL_EXE%" --kubeconfig "%KUBECONFIG%" get services
                                '''

                                echo 'Application successfully deployed on Azure AKS!'

                            } finally {

                                // DOCKER LOGOUT
                                echo 'Cleaning up Docker credentials...'

                                bat(
                                    returnStatus: true,
                                    script: '''
                                        @echo off

                                        "%DOCKER_EXE%" --config "%DOCKER_CONFIG%" logout "%ACR_LOGIN_SERVER%"
                                    '''
                                )

                                // REMOVE DOCKER CONFIG
                                bat(
                                    returnStatus: true,
                                    script: '''
                                        @echo off

                                        if exist "%DOCKER_CONFIG%" rmdir /s /q "%DOCKER_CONFIG%"
                                    '''
                                )

                                // AZURE LOGOUT
                                echo 'Logging out of Azure...'

                                bat(
                                    returnStatus: true,
                                    script: '"%AZ_CLI%" logout'
                                )

                                // REMOVE AZURE CONFIG WITH RETRIES
                                echo 'Removing temporary Azure configuration...'

                                bat(
                                    returnStatus: true,
                                    script: '''
                                        @echo off

                                        powershell.exe -NoProfile -NonInteractive -Command "$p=$env:AZURE_CONFIG_DIR; for($i=0;$i -lt 8;$i++){try{if(Test-Path -LiteralPath $p){Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction Stop};break}catch{Start-Sleep -Seconds 1}}"
                                    '''
                                )

                                // REMOVE KUBERNETES CONFIG
                                echo 'Removing temporary Kubernetes credentials...'

                                bat(
                                    returnStatus: true,
                                    script: '''
                                        @echo off

                                        if exist "%KUBECONFIG%" del /f /q "%KUBECONFIG%"

                                        if exist "%WORKSPACE%\\.kube" rmdir /s /q "%WORKSPACE%\\.kube"
                                    '''
                                )

                                // REMOVE TEMPORARY SCRIPT
                                bat(
                                    returnStatus: true,
                                    script: '''
                                        @echo off

                                        if exist "%WORKSPACE%\\acr-login.ps1" del /f /q "%WORKSPACE%\\acr-login.ps1"
                                    '''
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PIPELINE RESULT
    post {

        success {
            echo '''
            ==========================================
                  CIA2 CI/CD PIPELINE SUCCESSFUL
            ==========================================

                  GitHub Checkout         : SUCCESS
                  Java Environment        : SUCCESS
                  Maven Build & Tests     : SUCCESS
                  Docker Image Build      : SUCCESS
                  Azure Authentication   : SUCCESS
                  ACR Image Push          : SUCCESS
                  Kubernetes Deployment  : SUCCESS
                  Deployment Verification: SUCCESS

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

                  Check Jenkins Console Output
                  to identify the failed stage.

            ==========================================
            '''
        }

        always {
            echo 'Jenkins CI/CD pipeline execution finished.'
        }
    }
}
