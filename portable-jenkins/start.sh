#!/bin/bash
# -------------------------------
# Portable Jenkins Launcher (Linux/macOS)
# -------------------------------

# Get the folder where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Set JENKINS_HOME to a subfolder "jenkins_home"
export JENKINS_HOME="$SCRIPT_DIR/jenkins_home"

# Create jenkins_home if it doesn't exist
mkdir -p "$JENKINS_HOME"

# Ask user for Jenkins port
read -p "Enter Jenkins port: " JENKINS_PORT

PROJECT_DIR="$(cd "$SCRIPT_DIR/../" && pwd)"
# Copy pipeline Groovy files from project pipelines folder to jenkins_home/pipelines
mkdir -p "$JENKINS_HOME/pipelines"
cp -f "$PROJECT_DIR/pipelines/"*.groovy "$JENKINS_HOME/pipelines/"

# Copy pipeline config files from project pipeline-config folder to jenkins_home/pipeline-config
mkdir -p "$JENKINS_HOME/pipeline-config"
cp -f "$PROJECT_DIR/pipeline-config/"*.cfg "$JENKINS_HOME/pipeline-config/"

# Copy build Groovy files from project init.groovy.d folder to jenkins_home/init.groovy.d
mkdir -p "$JENKINS_HOME/init.groovy.d"
cp -f "$PROJECT_DIR/init.groovy.d/"*.groovy "$JENKINS_HOME/init.groovy.d/"

# Launch Jenkins
java --enable-future-java -Djenkins.install.runSetupWizard=false -Djenkins.home="$JENKINS_HOME" -jar "$SCRIPT_DIR/jenkins.war" --httpPort=$JENKINS_PORT > "$SCRIPT_DIR/jenkins.log" 2>&1 &

JENKINS_URL="http://localhost:$JENKINS_PORT"
echo "Open Jenkins at $JENKINS_URL"