FROM jenkins/jenkins:2.504.2-lts-jdk17

ARG LIBRARY_DIR=/library
ARG PLUGIN_FILE=/usr/share/jenkins/ref/plugins.txt
ARG CASC_FILE=/var/jenkins_home/casc_configs/jenkins.yaml
ARG INTEGRATION_DIR=./jenkins-integration-test

COPY . ${LIBRARY_DIR}

COPY ${INTEGRATION_DIR}/plugins.txt ${PLUGIN_FILE}
RUN jenkins-plugin-cli --plugin-file ${PLUGIN_FILE}

COPY ${INTEGRATION_DIR}/jenkins-casc.yaml ${CASC_FILE}

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

ENV CASC_JENKINS_CONFIG=${CASC_FILE}
