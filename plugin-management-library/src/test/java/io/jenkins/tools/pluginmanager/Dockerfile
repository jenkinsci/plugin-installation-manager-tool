# Start from a recent Jenkins LTS base
FROM jenkins/jenkins:2.452.1-jdk17

USER root

# Install dependencies
RUN apt-get update && apt-get install -y curl unzip

# Copy plugins.txt into image
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

# Download Plugin Installation Manager Tool
RUN curl -L -o /usr/local/bin/jenkins-plugin-manager.jar \
    https://github.com/jenkinsci/plugin-installation-manager-tool/releases/latest/download/jenkins-plugin-manager-2.12.10.jar

# Use PIMT to download and install all plugins
RUN java -jar /usr/local/bin/jenkins-plugin-manager.jar \
    --plugin-file /usr/share/jenkins/ref/plugins.txt \
    --plugin-download-directory /usr/share/jenkins/ref/plugins \
    --war /usr/share/jenkins/jenkins.war

# Download Jenkinsfile Runner
RUN curl -L https://repo.jenkins-ci.org/releases/io/jenkins/jenkinsfile-runner/1.0-beta-36/jenkinsfile-runner-1.0-beta-36.zip -o jfr.zip \
 && unzip jfr.zip -d /opt/jenkinsfile-runner && rm jfr.zip

# Set working dir
WORKDIR /workspace

# Default entrypoint to run Jenkinsfile
ENTRYPOINT ["/opt/jenkinsfile-runner/bin/jenkinsfile-runner"]
