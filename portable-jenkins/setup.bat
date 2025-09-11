@echo off
REM -------------------------------
REM Portable Jenkins Launcher (Windows)
REM -------------------------------

REM Get the folder where this script is located
SET SCRIPT_DIR=%~dp0

REM Set JENKINS_HOME to a subfolder "jenkins_home"
SET JENKINS_HOME=%SCRIPT_DIR%jenkins_home

REM Create jenkins_home if it doesn't exist
IF NOT EXIST "%JENKINS_HOME%" (
    mkdir "%JENKINS_HOME%"
)

REM Ask user for Jenkins port
SET /p JENKINS_PORT=Enter Jenkins port: 

REM Ask user for admin username/password
SET /p ADMIN_USER=Enter Jenkins admin username: 
SET /p ADMIN_PASS=Enter Jenkins admin password: 

REM Write a temporary init Groovy script
SET INIT_SCRIPT=%SCRIPT_DIR%\init_admin.groovy

REM Delete old init script if exists
IF EXIST "%INIT_SCRIPT%" del "%INIT_SCRIPT%"

REM Create a new init script
echo import jenkins.model.*>> "%INIT_SCRIPT%"
echo import hudson.security.*>> "%INIT_SCRIPT%"
echo.>> "%INIT_SCRIPT%"
echo def instance = Jenkins.instance>> "%INIT_SCRIPT%"
echo if (!(instance.securityRealm instanceof HudsonPrivateSecurityRealm)) {>> "%INIT_SCRIPT%"
echo     println "Creating admin user">> "%INIT_SCRIPT%"
echo     def hudsonRealm = new HudsonPrivateSecurityRealm(false)>> "%INIT_SCRIPT%"
echo     hudsonRealm.createAccount("%ADMIN_USER%", "%ADMIN_PASS%")>> "%INIT_SCRIPT%"
echo     instance.setSecurityRealm(hudsonRealm)>> "%INIT_SCRIPT%"
echo     def strategy = new FullControlOnceLoggedInAuthorizationStrategy()>> "%INIT_SCRIPT%"
echo     instance.setAuthorizationStrategy(strategy)>> "%INIT_SCRIPT%"
echo     instance.save()>> "%INIT_SCRIPT%"
echo     println "Admin user created!">> "%INIT_SCRIPT%"
echo } else {>> "%INIT_SCRIPT%"
echo     println "Security already configured, skipping...">> "%INIT_SCRIPT%"
echo }>> "%INIT_SCRIPT%"

REM Make sure init.groovy.d exists
IF NOT EXIST "%JENKINS_HOME%\init.groovy.d" mkdir "%JENKINS_HOME%\init.groovy.d"

REM Copy init scripts if needed
copy "%SCRIPT_DIR%\init_admin.groovy" "%JENKINS_HOME%\init.groovy.d"

REM Launch Jenkins
start "" /B java -Djenkins.install.runSetupWizard=false -jar "%SCRIPT_DIR%jenkins.war" --httpPort=%JENKINS_PORT% >> "%SCRIPT_DIR%jenkins-setup.log" 2>&1

REM Wait a few seconds for Jenkins to start
echo "Wait a few seconds for Jenkins to start"
timeout /t 30 /nobreak

SET CLI_JAR=%SCRIPT_DIR%jenkins-cli.jar
SET JENKINS_URL="http://localhost:%JENKINS_PORT%"
echo "Downloading jenkins-cli.jar (%JENKINS_URL%)"
powershell -Command "Invoke-WebRequest -Uri '%JENKINS_URL%/jnlpJars/jenkins-cli.jar' -OutFile '%CLI_JAR%'"

IF EXIST "%CLI_JAR%" (
    echo jenkins-cli.jar downloaded successfully!
) ELSE (
    echo Failed to download jenkins-cli.jar
)

REM Install plugins automatically
start "" /B java -jar "%SCRIPT_DIR%jenkins-cli.jar" -s %JENKINS_URL% -auth %ADMIN_USER%:%ADMIN_PASS% install-plugin workflow-aggregator -deploy >> "%SCRIPT_DIR%jenkins-plugins-setup.log" 2>&1

echo "Setup completed!!"
start "" "%JENKINS_URL%"
pause
exit