package pipelines.metadata

import pipelines.module.ModuleConfig

/**
 * Utility class for executing common Gradle tasks such as building Docker images
 * and running Sonar checks, centralizing Gradle command construction and execution.
 */
class GradleCommandUtil {
    static void executeGradleAction(ModuleConfig config, String moduleName, String task) {

        String taskName = task
        if (moduleName) {
            taskName = "${moduleName}:${task}"
        }

        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        def gradleCommand = "./gradlew -PprojectVersion=${projectVersion} -Prelease.version=${projectVersion} ${taskName}".toString()

        String proxyBasePath = CommonUtil.getProxyBasePath(config)
        if (!proxyBasePath.isEmpty()) {
            jenkins.echo("Using proxy base path: ${proxyBasePath}")
            gradleCommand = gradleCommand + " -Pdocker.proxyBasePath=${proxyBasePath}"
        }

        // handle task is 'dockerBuildImage -x check'
        String taskMainCommand = task.tokenize()[0]
        jenkins.steps.lock(resource: "${taskMainCommand}:${moduleName ?: 'root'}") {
            jenkins.sh """#!/bin/bash
                ${jenkins.ecrLogin()}
                ${gradleCommand}
            """
        }
    }

    /**
     * Executes Gradle tasks for SonarQube analysis and checks.
     *
     * @param config The ModuleConfig object containing Jenkins context and project configuration.
     * @param moduleName The name of the module for SonarQube analysis. If null, runs for the root project.
     */
    static void executeGradleCheckSonar(ModuleConfig config, String moduleName) {
        def jenkins = config.jenkins
        String gradleCommand = moduleName ?
                "./gradlew --no-daemon ${moduleName}:check ${moduleName}:sonar" :
                "./gradlew --no-daemon check sonar"

        jenkins.sh """#!/bin/bash
            set -euo pipefail
            ${jenkins.ecrLogin()}
            ${gradleCommand}
        """
    }
}
