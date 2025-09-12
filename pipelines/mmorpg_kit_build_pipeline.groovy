pipeline {
    agent any
    parameters {
        string(name: 'UNITY_PATH', defaultValue: 'C:\\Unity\\2022.3.55f1\\Editor\\Unity.exe', description: 'Full path to Unity executable')
        string(name: 'PROJECT_PATH', defaultValue: 'C:\\Projects\\Game', description: 'Full path to Unity project')
        string(name: 'BUILD_TARGET', defaultValue: 'LinuxServer', description: 'Target build platform (LinuxServer, WindowsServer, Android, iOS)')
        string(name: 'OUTPUT_PATH', defaultValue: 'C:\\Projects\\Build', description: 'Optional output path override')
        string(name: 'EXE_NAME', defaultValue: 'Build.x86_64', description: 'Optional executable name override (with extension)')
        string(name: 'BUNDLE_VERSION', defaultValue: '', description: 'Optional bundle version override')
        booleanParam(name: 'CLEAN_CONTENT', defaultValue: false, description: 'Clean Addressables build output before build')
        booleanParam(name: 'PURGE_CACHE', defaultValue: false, description: 'Purge global SBP build cache before build')
        booleanParam(name: 'PURGE_OUTPUT', defaultValue: true, description: 'Remove all files in OUTPUT_PATH before build')
        
        // Android keystore settings
        booleanParam(name: 'BUILD_APP_BUNDLE', defaultValue: false, description: 'Android: build as app bundle')
        string(name: 'KEYSTORE_NAME', defaultValue: '', description: 'Android keystore file path')
        string(name: 'KEYSTORE_PASS', defaultValue: '', description: 'Android keystore password')
        string(name: 'KEYALIAS_NAME', defaultValue: '', description: 'Android key alias')
        string(name: 'KEYALIAS_PASS', defaultValue: '', description: 'Android key alias password')

        // Android / iOS version code
        string(name: 'VERSION_CODE', defaultValue: '', description: 'Optional version code override')

        // Docker parameters
        booleanParam(name: 'GENERATE_MAP_SERVER_DOCKERFILE', defaultValue: true, description: 'Will generate map-server Dockerfile or not?')
        string(name: 'MAP_SERVER_PORT_IN_DOCKERFILE', defaultValue: '6000', description: 'Map-server port in Dockerfile')
        string(name: 'MAP_SERVER_DOCKER_IMAGE', defaultValue: 'unity-server', description: 'Docker image name (without registry)')
        string(name: 'MAP_SERVER_DOCKER_TAG', defaultValue: 'latest', description: 'Docker image tag')
        string(name: 'DOCKER_USER', defaultValue: 'username', description: 'Docker username')
        string(name: 'DOCKER_PASS', defaultValue: 'password', description: 'Docker password')

        // Git parameters
        booleanParam(name: 'GIT_PULL', defaultValue: true, description: 'Pull latest changes from remote repository')
        string(name: 'GIT_BRANCH', defaultValue: 'main', description: 'Git branch to checkout')
        string(name: 'GIT_USER', defaultValue: 'username', description: 'Git username')
        string(name: 'GIT_MAIL', defaultValue: 'name@domain.com', description: 'Git email')
        string(name: 'GIT_PASS', defaultValue: 'password', description: 'Git password')
    }

    stages {
        stage('Git Checkout') {
            steps {
                script {
                    if (params.GIT_PULL) {
                        dir(params.PROJECT_PATH) {
                            if (params.GIT_USER?.trim()) {
                                if (isUnix()) {
                                    sh "git config --global user.name \"${params.GIT_USER}\""
                                } else {
                                    bat "git config --global user.name \"${params.GIT_USER}\""
                                }
                            }
                            if (params.GIT_MAIL?.trim()) {
                                if (isUnix()) {
                                    sh "git config --global user.email \"${params.GIT_MAIL}\""
                                } else {
                                    bat "git config --global user.email \"${params.GIT_MAIL}\""
                                }
                            }
                            if (params.GIT_PASS?.trim()) {
                                if (isUnix()) {
                                    sh "git config --global user.password \"${params.GIT_PASS}\""
                                } else {
                                    bat "git config --global user.password \"${params.GIT_PASS}\""
                                }
                            }
                             if (isUnix()) {
                                 sh """
                                     git fetch --all
                                     git reset --hard HEAD
                                     git clean -fd
                                     git checkout ${params.GIT_BRANCH}
                                     git pull origin ${params.GIT_BRANCH}
                                 """
                             } else {
                                 bat """
                                     git fetch --all
                                     git reset --hard HEAD
                                     git clean -fd
                                     git checkout ${params.GIT_BRANCH}
                                     git pull origin ${params.GIT_BRANCH}
                                 """
                             }
                        }
                    }
                }
            }
        }

        stage('Purge Output Directory') {
            when {
                expression { params.PURGE_OUTPUT }
            }
            steps {
                script {
                    if (params.OUTPUT_PATH?.trim()) {
                        echo "Purging output directory: ${params.OUTPUT_PATH}"
                        if (isUnix()) {
                            sh """
                                if [ -d "${params.OUTPUT_PATH}" ]; then
                                    echo "Removing all files from ${params.OUTPUT_PATH}"
                                    rm -rf "${params.OUTPUT_PATH}"/*
                                    echo "Output directory purged successfully"
                                else
                                    echo "Output directory does not exist: ${params.OUTPUT_PATH}"
                                    mkdir -p "${params.OUTPUT_PATH}"
                                    echo "Created output directory: ${params.OUTPUT_PATH}"
                                fi
                            """
                        } else {
                            bat """
                                if exist "${params.OUTPUT_PATH}" (
                                    echo Removing all files from ${params.OUTPUT_PATH}
                                    del /q /s "${params.OUTPUT_PATH}\\*" 2>nul
                                    for /d %%i in ("${params.OUTPUT_PATH}\\*") do rmdir /s /q "%%i" 2>nul
                                    echo Output directory purged successfully
                                ) else (
                                    echo Output directory does not exist: ${params.OUTPUT_PATH}
                                    mkdir "${params.OUTPUT_PATH}"
                                    echo Created output directory: ${params.OUTPUT_PATH}
                                )
                            """
                        }
                    } else {
                        echo "No OUTPUT_PATH specified, skipping purge"
                    }
                }
            }
        }

        stage('Build Unity') {
            steps {
                script {
                    def unityCmd = "\"${params.UNITY_PATH}\" -batchmode -nographics -quit -projectPath \"${params.PROJECT_PATH}\""

                    if (params.OUTPUT_PATH?.trim()) unityCmd += " -outputPath \"${params.OUTPUT_PATH}\""
                    if (params.EXE_NAME?.trim()) unityCmd += " -exeName \"${params.EXE_NAME}\""
                    if (params.BUNDLE_VERSION?.trim()) unityCmd += " -bundleversion \"${params.BUNDLE_VERSION}\""
                    if (params.CLEAN_CONTENT) unityCmd += " -cleanContent true"
                    if (params.PURGE_CACHE) unityCmd += " -purgeCache true"

                    switch(params.BUILD_TARGET) {
                        case 'LinuxServer':
                            unityCmd += " -executeMethod MultiplayerARPG.Builder.BuildLinux64Server"
                            if (params.GENERATE_MAP_SERVER_DOCKERFILE) unityCmd += " -generateMapServerDockerfile true"
                            if (params.GENERATE_MAP_SERVER_DOCKERFILE) unityCmd += " -mapServerPortInDockerfile \"${params.MAP_SERVER_PORT_IN_DOCKERFILE}\""
                            break
                        case 'WindowsServer':
                            unityCmd += " -executeMethod MultiplayerARPG.Builder.BuildWindows64Server"
                            if (params.GENERATE_MAP_SERVER_DOCKERFILE) unityCmd += " -generateMapServerDockerfile true"
                            if (params.GENERATE_MAP_SERVER_DOCKERFILE) unityCmd += " -mapServerPortInDockerfile \"${params.MAP_SERVER_PORT_IN_DOCKERFILE}\""
                            break
                        case 'Android':
                            unityCmd += " -executeMethod MultiplayerARPG.Builder.BuildAndroid"
                            if (params.BUILD_APP_BUNDLE) unityCmd += " -buildAppBundle true"
                            if (params.KEYSTORE_NAME?.trim()) unityCmd += " -keystoreName \"${params.KEYSTORE_NAME}\""
                            if (params.KEYSTORE_PASS?.trim()) unityCmd += " -keystorePass \"${params.KEYSTORE_PASS}\""
                            if (params.KEYALIAS_NAME?.trim()) unityCmd += " -keyaliasName \"${params.KEYALIAS_NAME}\""
                            if (params.KEYALIAS_PASS?.trim()) unityCmd += " -keyaliasPass \"${params.KEYALIAS_PASS}\""
                            if (params.VERSION_CODE?.trim()) unityCmd += " -versionCode \"${params.VERSION_CODE}\""
                            break
                        case 'iOS':
                            unityCmd += " -executeMethod MultiplayerARPG.Builder.BuildiOS"
                            if (params.VERSION_CODE?.trim()) unityCmd += " -versionCode \"${params.VERSION_CODE}\""
                            break
                    }

                    if (isUnix()) {
                        sh unityCmd
                    } else {
                        bat unityCmd
                    }
                }
            }
        }

        stage('Docker Build') {
            when {
                allOf {
                    expression { params.GENERATE_MAP_SERVER_DOCKERFILE }
                    expression { params.BUILD_TARGET == 'LinuxServer' || params.BUILD_TARGET == 'WindowsServer' }
                }
            }
            steps {
                script {
                    if (isUnix()) {
                        sh """
                            docker build -t ${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG} ${params.OUTPUT_PATH}
                        """
                    } else {
                        bat """
                            docker build -t ${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG} ${params.OUTPUT_PATH}
                        """
                    }
                }
            }
        }

        stage('Docker Push') {
            when {
                allOf {
                    expression { params.GENERATE_MAP_SERVER_DOCKERFILE }
                    expression { params.BUILD_TARGET == 'LinuxServer' || params.BUILD_TARGET == 'WindowsServer' }
                }
            }
            steps {
                script {
                    if (params.DOCKER_USER?.trim() && params.DOCKER_PASS?.trim()) {
                        if (isUnix()) {
                            sh "docker login -u \"${params.DOCKER_USER}\" -p \"${params.DOCKER_PASS}\""
                        } else {
                            bat "docker login -u \"${params.DOCKER_USER}\" -p \"${params.DOCKER_PASS}\""
                        }
                    }
                    if (isUnix()) {
                        sh """
                            docker tag ${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG} ${params.DOCKER_USER}/${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG}
                            docker push ${params.DOCKER_USER}/${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG}
                            docker logout
                        """
                    } else {
                        bat """
                            docker tag ${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG} ${params.DOCKER_USER}/${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG}
                            docker push ${params.DOCKER_USER}/${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG}
                            docker logout
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            echo "Pipeline finished."
        }
    }
}
