# From 'plugin-management-cli' dir:
# build:
#   docker build -t jnks-plugin-tool -f src/test/docker/Dockerfile .
# run:
#   docker run --rm -it jnks-plugin-tool
#   docker run --rm -it --entrypoint bash jnks-plugin-tool
#
FROM jenkins/jenkins:lts
COPY target/jenkins-plugin-manager-*.jar /opt/jenkins-plugin-manager.jar
WORKDIR $JENKINS_HOME
ENTRYPOINT ["java", "-jar", "/opt/jenkins-plugin-manager.jar"]