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

# Ask user for admin username/password
read -p "Enter Jenkins admin username: " ADMIN_USER
read -s -p "Enter Jenkins admin password: " ADMIN_PASS
echo ""

# Write a temporary init Groovy script
INIT_SCRIPT="$SCRIPT_DIR/init_admin.groovy"

# Delete old init script if exists
[ -f "$INIT_SCRIPT" ] && rm "$INIT_SCRIPT"

# Create a new init script
cat > "$INIT_SCRIPT" <<EOL
import jenkins.model.*
import hudson.security.*

def instance = Jenkins.instance
if (!(instance.securityRealm instanceof HudsonPrivateSecurityRealm)) {
    println "Creating admin user"
    def hudsonRealm = new HudsonPrivateSecurityRealm(false)
    hudsonRealm.createAccount("${ADMIN_USER}", "${ADMIN_PASS}")
    instance.setSecurityRealm(hudsonRealm)
    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    instance.setAuthorizationStrategy(strategy)
    instance.save()
    println "Admin user created!"
} else {
    println "Security already configured, skipping..."
}
EOL

# Make sure init.groovy.d exists
mkdir -p "$JENKINS_HOME/init.groovy.d"

# Copy init script into init.groovy.d
cp "$INIT_SCRIPT" "$JENKINS_HOME/init.groovy.d/"

# Launch Jenkins in background with logging
java -Djenkins.install.runSetupWizard=false -Djenkins.home="$JENKINS_HOME" -jar "$SCRIPT_DIR/jenkins.war" --enable-future-java --httpPort=$JENKINS_PORT > "$SCRIPT_DIR/jenkins-setup.log" 2>&1 &

echo "Wait a few seconds for Jenkins to start..."
sleep 30

# Download Jenkins CLI
CLI_JAR="$SCRIPT_DIR/jenkins-cli.jar"
JENKINS_URL="http://localhost:$JENKINS_PORT"
echo "Downloading jenkins-cli.jar ($JENKINS_URL)"
curl -sSL "$JENKINS_URL/jnlpJars/jenkins-cli.jar" -o "$CLI_JAR"

if [ -f "$CLI_JAR" ]; then
    echo "jenkins-cli.jar downloaded successfully!"
else
    echo "Failed to download jenkins-cli.jar"
fi

# Install plugins automatically
java -jar "$CLI_JAR" --enable-future-java -s "$JENKINS_URL" -auth "$ADMIN_USER:$ADMIN_PASS" install-plugin workflow-aggregator -deploy > "$SCRIPT_DIR/jenkins-plugins-setup.log" 2>&1

echo "Setup completed!!"
echo "Open Jenkins at $JENKINS_URL"