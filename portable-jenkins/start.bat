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

SET PROJECT_DIR=%SCRIPT_DIR%..
REM Copy pipeline Groovy files from project pipelines folder to jenkins_home/pipelines
IF NOT EXIST "%JENKINS_HOME%\pipelines" (
    mkdir "%JENKINS_HOME%\pipelines"
)

REM Copy all .groovy files
xcopy "%PROJECT_DIR%\pipelines\*.groovy" "%JENKINS_HOME%\pipelines\" /Y /S

REM Copy pipeline config files from project pipeline-config folder to jenkins_home/pipeline-config
IF NOT EXIST "%JENKINS_HOME%\pipeline-config" (
    mkdir "%JENKINS_HOME%\pipeline-config"
)

REM Copy all .txt files
xcopy "%PROJECT_DIR%\pipeline-config\*.cfg" "%JENKINS_HOME%\pipeline-config\" /Y /S

REM Copy build Groovy files from project init.groovy.d folder to jenkins_home/init.groovy.d
IF NOT EXIST "%JENKINS_HOME%\init.groovy.d" (
    mkdir "%JENKINS_HOME%\init.groovy.d"
)

REM Copy all .groovy files
xcopy "%PROJECT_DIR%\init.groovy.d\*.groovy" "%JENKINS_HOME%\init.groovy.d\" /Y /S

REM Launch Jenkins
start "" /B java -Djenkins.install.runSetupWizard=false -jar "%SCRIPT_DIR%jenkins.war" --enable-future-java --httpPort=%JENKINS_PORT% >> "%SCRIPT_DIR%jenkins.log" 2>&1

SET JENKINS_URL="http://localhost:%JENKINS_PORT%"
start "" "%JENKINS_URL%"
pause
exit