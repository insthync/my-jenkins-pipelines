pipeline {
    agent any
    parameters {
        string(name: 'UNITY_PATH', defaultValue: 'C:\\Unity\\2022.3.55f1\\Editor\\Unity.exe', description: 'Full path to Unity executable')
        string(name: 'PROJECT_PATH', defaultValue: 'C:\\Projects\\Game', description: 'Full path to Unity project')
        choice(name: 'BUILD_TARGET', choices: ['LinuxServer', 'WindowsServer', 'Android', 'iOS'], description: 'Target build platform')
        string(name: 'OUTPUT_PATH', defaultValue: 'C:\\Projects\\Build', description: 'Optional output path override')
        string(name: 'EXE_NAME'), defaultValue: 'Build.x86_64', description: 'Optional executable name override (with extension)')
        string(name: 'BUNDLE_VERSION', defaultValue: '', description: 'Optional bundle version override')
        booleanParam(name: 'CLEAN_CONTENT', defaultValue: false, description: 'Clean Addressables build output before build')
        booleanParam(name: 'PURGE_CACHE', defaultValue: false, description: 'Purge global SBP build cache before build')
        
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
    }

    stages {
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
                    sh """
                        docker build -t ${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG} \
                            ${params.OUTPUT_PATH}
                    """
                }
            }
        }

        stage('Docker Push') {
            when {
                expression { params.BUILD_TARGET == 'LinuxServer' || params.BUILD_TARGET == 'WindowsServer' }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh """
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker tag ${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG} $DOCKER_USER/${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG}
                        docker push $DOCKER_USER/${params.MAP_SERVER_DOCKER_IMAGE}:${params.MAP_SERVER_DOCKER_TAG}
                        docker logout
                    """
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
