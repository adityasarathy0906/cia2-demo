
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }
           triggers {
        pollSCM('H/2 * * * *')
    }
    environment {
        // AZURE CLI
        AZ_CLI = 'C:\\Program Files\\Microsoft SDKs\\Azure\\CLI2\\wbin\\az.cmd'

        // DOCKER DESKTOP
        DOCKER_EXE = 'C:\\Users\\acer\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'

        // KUBERNETES CLI
        KUBECTL_EXE = 'C:\\Users\\acer\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Kubernetes.kubectl_Microsoft.Winget.Source_8wekyb3d8bbwe\\kubectl.exe'

        // AZURE DETAILS
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

        // STAGE 2: JAVA
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
                        "DOCKER_CONFIG=${env.WORKSPACE}\\.docker-ci",
                        "AZURE_CONFIG_DIR=${env.WORKSPACE}\\.azure-ci"
                    ]) {

                        script {

                            def workspaceKubeconfig =
                                "${env.WORKSPACE}\\.kube\\config"

                            try {

                                // INITIALIZE CONFIGURATION DIRECTORIES
                                echo 'Preparing isolated configurations...'

                                bat '''
                                    @echo off

                                    if not exist "%DOCKER_CONFIG%" mkdir "%DOCKER_CONFIG%"

                                    if not exist "%AZURE_CONFIG_DIR%" mkdir "%AZURE_CONFIG_DIR%"

                                    if not exist "%WORKSPACE%\\.kube" mkdir "%WORKSPACE%\\.kube"

                                    > "%DOCKER_CONFIG%\\config.json" echo {}
                                '''

                                // DISABLE AZURE TELEMETRY
                                writeFile(
                                    file: '.azure-ci/config',
                                    text: '[core]\ncollect_telemetry=no\n'
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

                                // ACR TOKEN AUTHENTICATION
                                echo 'Obtaining ACR token...'

                                writeFile file: 'acr-login.ps1', text: '''
$ErrorActionPreference = "Stop"

$token = & $env:AZ_CLI acr login `
    --name $env:ACR_NAME `
    --expose-token `
    --query accessToken `
    --output tsv

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($token)) {
    Write-Error "Could not obtain ACR token."
    exit 1
}

$token | & $env:DOCKER_EXE --config $env:DOCKER_CONFIG login `
    $env:ACR_LOGIN_SERVER `
    --username "00000000-0000-0000-0000-000000000000" `
    --password-stdin

if ($LASTEXITCODE -ne 0) {
    Write-Error "ACR Docker login failed."
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
                                echo 'Pushing versioned Docker image...'

                                bat '''
                                    @echo off

                                    "%DOCKER_EXE%" --config "%DOCKER_CONFIG%" push "%IMAGE_URI%"

                                    if errorlevel 1 exit /b 1
                                '''

                                // PUSH LATEST IMAGE
                                echo 'Pushing latest Docker image...'

                                bat '''
                                    @echo off

                                    "%DOCKER_EXE%" --config "%DOCKER_CONFIG%" push "%IMAGE_LATEST%"

                                    if errorlevel 1 exit /b 1
                                '''

                                echo 'Both images successfully pushed to ACR!'

                                // RETRIEVE AKS CREDENTIALS
                                echo 'Retrieving AKS credentials...'

                                bat """
                                    @echo off

                                    if not exist "%WORKSPACE%\\.kube" mkdir "%WORKSPACE%\\.kube"

                                    "%AZ_CLI%" aks get-credentials ^
                                    --resource-group "%AZURE_RESOURCE_GROUP%" ^
                                    --name "%AKS_NAME%" ^
                                    --file "${workspaceKubeconfig}" ^
                                    --overwrite-existing

                                    if errorlevel 1 exit /b 1

                                    if not exist "${workspaceKubeconfig}" (
                                        echo ERROR: Kubeconfig was not generated.
                                        exit /b 1
                                    )
                                """

                                // POWERSHELL KUBERNETES DEPLOYMENT SCRIPT
                                echo 'Preparing Kubernetes deployment...'

                                writeFile file: 'aks-deploy.ps1', text: '''
$ErrorActionPreference = "Stop"

$kubectl = $env:KUBECTL_EXE

# WORKSPACE KUBECONFIG
$workspaceConfig = Join-Path $env:WORKSPACE ".kube\\config"

# JENKINS SYSTEM ACCOUNT DEFAULT KUBECONFIG
$defaultKubeDir = Join-Path $env:USERPROFILE ".kube"
$defaultConfig = Join-Path $defaultKubeDir "config"

Write-Output "Workspace kubeconfig: $workspaceConfig"
Write-Output "Jenkins default kubeconfig: $defaultConfig"

# VERIFY WORKSPACE CONFIGURATION
if (-not (Test-Path -LiteralPath $workspaceConfig)) {
    Write-Error "Workspace kubeconfig does not exist."
    exit 1
}

# CREATE THE SYSTEM ACCOUNT KUBE DIRECTORY
if (-not (Test-Path -LiteralPath $defaultKubeDir)) {
    New-Item -ItemType Directory -Path $defaultKubeDir -Force | Out-Null
}

# COPY CONFIGURATION TO THE DEFAULT LOCATION
Copy-Item -LiteralPath $workspaceConfig -Destination $defaultConfig -Force

if (-not (Test-Path -LiteralPath $defaultConfig)) {
    Write-Error "Could not copy kubeconfig to Jenkins system profile."
    exit 1
}

# SET KUBECONFIG EXPLICITLY
$env:KUBECONFIG = $defaultConfig

Write-Output "Using kubeconfig: $env:KUBECONFIG"

# VERIFY KUBERNETES CONNECTION
Write-Output "Checking AKS nodes..."

& $kubectl --kubeconfig $defaultConfig get nodes

if ($LASTEXITCODE -ne 0) {
    Write-Error "AKS node verification failed."
    exit 1
}

# IDENTIFY CONTAINER
Write-Output "Identifying Kubernetes container..."

$containerName = (& $kubectl --kubeconfig $defaultConfig get deployment $env:DEPLOYMENT_NAME -o 'jsonpath={.spec.template.spec.containers[0].name}' | Out-String).Trim()

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerName)) {
    Write-Error "Could not identify deployment container."
    exit 1
}

Write-Output "Container identified: $containerName"

# UPDATE DEPLOYMENT IMAGE
Write-Output "Updating Kubernetes deployment..."

& $kubectl --kubeconfig $defaultConfig set image "deployment/$($env:DEPLOYMENT_NAME)" "$($containerName)=$($env:IMAGE_URI)"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Deployment image update failed."
    exit 1
}

# VERIFY ROLLOUT
Write-Output "Waiting for Kubernetes rollout..."

& $kubectl --kubeconfig $defaultConfig rollout status "deployment/$($env:DEPLOYMENT_NAME)" --timeout=180s

if ($LASTEXITCODE -ne 0) {
    Write-Error "Kubernetes rollout failed."
    exit 1
}

# FINAL VERIFICATION
Write-Output "===== DEPLOYMENTS ====="

& $kubectl --kubeconfig $defaultConfig get deployments

if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Output "===== PODS ====="

& $kubectl --kubeconfig $defaultConfig get pods -o wide

if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Output "===== SERVICES ====="

& $kubectl --kubeconfig $defaultConfig get services

if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Output "Kubernetes deployment successful!"
'''

                                // RUN KUBERNETES SCRIPT
                                echo 'Connecting to AKS and deploying application...'

                                bat '''
                                    @echo off

                                    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%WORKSPACE%\\aks-deploy.ps1"

                                    if errorlevel 1 exit /b 1
                                '''

                                echo 'Application successfully deployed on Azure AKS!'

                            } finally {

                                // DOCKER LOGOUT
                                echo 'Cleaning Docker credentials...'

                                bat(
                                    returnStatus: true,
                                    script: '"%DOCKER_EXE%" --config "%DOCKER_CONFIG%" logout "%ACR_LOGIN_SERVER%"'
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

                                // REMOVE AZURE CONFIG
                                bat(
                                    returnStatus: true,
                                    script: '''
                                        @echo off

                                        powershell.exe -NoProfile -NonInteractive -Command "$p=$env:AZURE_CONFIG_DIR; for($i=0;$i -lt 10;$i++){try{if(Test-Path -LiteralPath $p){Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction Stop};break}catch{Start-Sleep -Seconds 1}}"
                                    '''
                                )

                                // REMOVE WORKSPACE KUBECONFIG
                                bat(
                                    returnStatus: true,
                                    script: """
                                        @echo off

                                        if exist "${workspaceKubeconfig}" del /f /q "${workspaceKubeconfig}"

                                        if exist "%WORKSPACE%\\.kube" rmdir /s /q "%WORKSPACE%\\.kube"
                                    """
                                )

                                // REMOVE SYSTEM PROFILE KUBECONFIG
                                bat(
                                    returnStatus: true,
                                    script: '''
                                        @echo off

                                        powershell.exe -NoProfile -NonInteractive -Command "$p=Join-Path $env:USERPROFILE '.kube\\config'; if(Test-Path -LiteralPath $p){Remove-Item -LiteralPath $p -Force}"
                                    '''
                                )

                                // REMOVE TEMPORARY POWERSHELL FILES
                                bat(
                                    returnStatus: true,
                                    script: '''
                                        @echo off

                                        if exist "%WORKSPACE%\\acr-login.ps1" del /f /q "%WORKSPACE%\\acr-login.ps1"

                                        if exist "%WORKSPACE%\\aks-deploy.ps1" del /f /q "%WORKSPACE%\\aks-deploy.ps1"
                                    '''
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PIPELINE STATUS
    post {

        success {
            echo '''
            ==========================================
                 CIA2 CI/CD PIPELINE SUCCESSFUL
            ==========================================

                 GitHub Checkout        : SUCCESS
                 Java Environment       : SUCCESS
                 Maven Build & Tests    : SUCCESS
                 Docker Image Build     : SUCCESS
                 Azure Authentication  : SUCCESS
                 ACR Image Push         : SUCCESS
                 AKS Connection         : SUCCESS
                 Kubernetes Deployment : SUCCESS
                 Rollout Verification   : SUCCESS

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
