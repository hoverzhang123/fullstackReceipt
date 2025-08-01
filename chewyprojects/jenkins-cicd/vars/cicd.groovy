@java.lang.SuppressWarnings('NoScriptBindings')

import static pipelines.github.Github.updateComment
import static pipelines.github.Repository.isDefaultBranch

import groovy.transform.Field
import pipelines.GatlingModuleConfig
import pipelines.Pipeline
import pipelines.PipelineConfig
import pipelines.PlaywrightModuleConfig
import pipelines.module.DockerModule
import pipelines.module.AptHelmModule
import pipelines.module.E2EModule
import pipelines.module.FlywayModule
import pipelines.module.GatlingModule
import pipelines.module.HelmModule
import pipelines.module.LambdaModule
import pipelines.module.LibraryModule
import pipelines.module.Module
import pipelines.module.ModuleFactory
import pipelines.module.PlaywrightModule
import pipelines.module.DatadogMonitorModule
import pipelines.module.DynatraceMonitorModule
import pipelines.module.ReactModule
import pipelines.module.SpecModule
import pipelines.module.TerraformModule

@Field
        def ACCOUNTS = [
            shd: '278833423079',
            dev: '243249644484',
            qat: '977618051197',
            stg: '035366186883',
            prd: '157117485830',
        ]

@Field
        def REGIONS = [
            "us-east-1": "use1",
            "us-east-2": "use2",
        ]

@Field
        String slackChannel = 'rxp-notifications'
@Field
        String prodSlackChannel = 'rxp-notifications'

@Field
        boolean failureNotified = false

def init(Map props) {
    echo "init props: ${props}"
    stage("Init") {
        library 'jenkins@main'
        library 'jenkins-library'
        library(identifier: 'innersource-jenkins-utils@main', retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: 'https://github.com/Chewy-Inc/innersource-jenkins-utils.git',
            credentialsId: 'jenkins-github-userpass'
        ]))
        library identifier: 'red-supergiant@1.5.0', retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: 'https://github.com/Chewy-Inc/red-supergiant.git',
            credentialsId: 'jenkins-github-userpass'])
    }
    List<Module> modules = []
    if (props?.slackParams?.channel) {
        slackChannel = props.slackParams.channel
    }
    // fall back to slackChannel if `slackParams.prodChannel` isn't specified
    prodSlackChannel = props?.slackParams?.prodChannel ?: slackChannel

    if (props.appModule) {
        String appModule = props.appModule
        modules.add(DockerModule.builder()
                .name(appModule)
                .moduleName("DockerModule")
                .build())
        modules.add(new HelmModule(appModule))
        modules.add(DatadogMonitorModule.builder()
                .build())
    }
    if (props.aptAppModule) {
        def aptAppModule = props.aptAppModule

        modules.add(DockerModule.builder()
                .name(aptAppModule.name)
                .moduleName("AptDockerModule")
                .build())
        modules.add(AptHelmModule.builder()
                .namespace(aptAppModule.namespace)
                .featureDeployHelmName(aptAppModule.featureDeployHelmName)
                .hostPath(aptAppModule.hostPath)
                .serviceModuleName(aptAppModule.helmDirectory)
                .helmChartSuffix(aptAppModule.helmChartSuffix)
                .helmTimeOut(aptAppModule.helmTimeOut)
                .build())
        if (!aptAppModule.disableDatadog) {
            modules.add(DatadogMonitorModule.builder()
                    .postCheckAttemptsNonPrd(aptAppModule.postCheckAttemptsNonPrd)
                    .postCheckIntervalSecondsNonPrd(aptAppModule.postCheckIntervalSecondsNonPrd)
                    .postCheckAttemptsPrd(aptAppModule.postCheckAttemptsPrd)
                    .postCheckIntervalSecondsPrd(aptAppModule.postCheckIntervalSecondsPrd)
                    .prdFailureStatus(aptAppModule.prdFailureStatus)
                    .nonPrdFailureStatus(aptAppModule.nonPrdFailureStatus)
                    .autoRollbackOnFailure(aptAppModule.autoRollbackOnFailure)
                    .build())
        }
    }
    if (props.gatlingModule) {
        props.gatlingModule.each { config ->
            def moduleConfig = GatlingModuleConfig.builder()
                    .name(config.name)
                    .simulationId(config.simulationId)
                    .slackChannel(props.slackParams.channel)
                    .disableSlackNotifications(config.disableSlackNotifications)
                    .autoRollbackOnFailure(config.autoRollbackOnFailure)
                    .runInBranchBuild(config.runInBranchBuild)
                    .runInMainBuild(config.runInMainBuild)
                    .runInMergeQueueBuild(config.runInMergeQueueBuild)
                    .runInPrBuild(config.runInPrBuild)
                    .failureStatus(config.failureStatus)
                    .build()
            modules.add(new GatlingModule(moduleConfig))
        }
    }
    if (props.dynatraceMonitorModule) {
        def dynatraceMonitorModule = props.dynatraceMonitorModule
        modules.add(DynatraceMonitorModule.builder()
                .postCheckAttemptsNonPrd(dynatraceMonitorModule.postCheckAttemptsNonPrd)
                .postCheckIntervalSecondsNonPrd(dynatraceMonitorModule.postCheckIntervalSecondsNonPrd)
                .postCheckAttemptsPrd(dynatraceMonitorModule.postCheckAttemptsPrd)
                .postCheckIntervalSecondsPrd(dynatraceMonitorModule.postCheckIntervalSecondsPrd)
                .prdFailureStatus(dynatraceMonitorModule.prdFailureStatus)
                .nonPrdFailureStatus(dynatraceMonitorModule.nonPrdFailureStatus)
                .autoRollbackOnFailure(dynatraceMonitorModule.autoRollbackOnFailure)
                .enableDeployObservability(dynatraceMonitorModule.enableDeployObservability)
                .build())
    }
    if (props.terraformModule) {
        modules.add(new TerraformModule(props.terraformModule as String))
    }
    if (props.flywayModule) {
        modules.add(new FlywayModule(props.flywayModule as String))
    }
    if (props.specModule) {
        modules.add(new SpecModule(props.specModule as String))
    }
    if (props.e2eModule) {
        List<String> testEnvironments = (props.e2eTestEnvironments ?: props.deploymentLowerEnvironments.flatten()) as List<String>
        modules.add(new E2EModule(props.e2eModule as String, testEnvironments))
    }
    def playwrightSetup
    if (props.playwrightSetup) {
        playwrightSetup = props.playwrightSetup
    }

    if (props.playwrightModule) {

        boolean isProdEnv = env.ENVIRONMENT == 'prd'
        props.playwrightModule.each { config ->
            def isProdSuite = config.testSuites.any { it.contains('prod') }
            boolean shouldRunTest = (isProdSuite && isProdEnv) || (!isProdSuite && !isProdEnv)

            if (shouldRunTest) {
                def moduleConfig = PlaywrightModuleConfig.builder()
                        .name(config.testSuites.collect { it.toString() }.join(" "))
                        .testSuites(config.testSuites)
                        .testTargetName(config.testTargetName)
                        .secretKeys(playwrightSetup.secretKeys)
                        .envMap(playwrightSetup.envMap)
                        .playwrightImage(playwrightSetup.playwrightImage)
                        .autoRollbackOnFailure(playwrightSetup.autoRollbackOnFailure)
                        .runInPRBuild(playwrightSetup.runInPRBuild)
                        .prdFailureStatus(playwrightSetup.prdFailureStatus)
                        .nonPrdFailureStatus(playwrightSetup.nonPrdFailureStatus)
                        .build()
                modules.add(new PlaywrightModule(moduleConfig))
            }
        }
    }
    if (props.lambdaModule) {
        modules.add(new LambdaModule(props.lambdaModule as String))
    }
    if (props.libraryModule && props.libraryModule instanceof String) {
        modules.add(new LibraryModule([props.libraryModule as String]))
    }
    if (props.libraryModule && props.libraryModule instanceof List) {
        modules.add(new LibraryModule(props.libraryModule as List<String>))
    }
    if (props.reactModule) {
        String reactModule = props.reactModule
        modules.add(new ReactModule(reactModule))
        modules.add(new HelmModule(reactModule))
        modules.add(DatadogMonitorModule.builder()
                .build())
    }

    PipelineConfig pipelineConfig = PipelineConfig.builder()
            .jenkins(this)
            .projectName(props.projectName as String)
            .regions(props.regions as List<String>)
            .assignmentGroup(props.assignmentGroup as String)
            .modules(modules)
            .vertical(props.vertical as String)
            .clusterName(props.clusterName as String)
            .namespace(props.namespace as String)
            .mergeQueueDeploymentEnvs(props.mergeQueueDeploymentEnvs as List<String>)
            .deploymentLowerEnvironments(props.deploymentLowerEnvironments as List<List<String>>)
            .skipDeploymentGatesLowerEnvs(props.skipDeploymentGatesLowerEnvs as List<String>)
            .featureBranchDeploymentEnvs(props.featureBranchDeploymentEnvs as List<String>)
            .respectChangeFreezesProd(props.respectChangeFreezesProd)
            .respectChangeFreezesNonprod(props.respectChangeFreezesNonprod)
            .postParams(props.postParams)
            .enableFeatureBranchBuilds(props.enableFeatureBranchBuilds)
            .slackParams(props.slackParams)
            .versionIncrement(props.versionIncrement as String)
            .build()

    if (props.modules) {
        def rawModules = props.modules as Collection<Object>
        rawModules.each { module ->
            if (module instanceof Module) {
                modules.add(module)
            } else {
                modules.add(ModuleFactory.createModule(pipelineConfig, module))
            }
        }
    }

    def pipeline = new Pipeline(pipelineConfig)

    echo("k8s.isProdEnvironment(): ${k8s.isProdEnvironment()}")
    echo("env.ENVIRONMENT: ${env.ENVIRONMENT}")
    if (env.ENVIRONMENT.equals("prd") || k8s.isProdEnvironment()) {
        env.ENVIRONMENT = "prd"
        parameters = pipeline.prodParameters()
    } else {
        parameters = pipeline.defaultParameters()
    }
    stage("Initialize Deployment Parameters") {
        properties([parameters])
    }

    return pipeline
}

// Notifications

def notify(String projectName, String message, String color, Boolean mentionUser = false, Boolean priorityNotification = false) {
    String userEmail = env.USER_EMAIL
    def userId = getSlackUserFromEmail(userEmail)
    sendSlackMessage("[${projectName}] ${message} ${mentionUser ? userId : ""}", color, useProdChannel(priorityNotification))
}

def notifyFailure(String projectName, String message, Boolean alwaysNotify = false) {
    def userId = ""
    if (env.USER_EMAIL != null) {
        userId = getSlackUserFromEmail(env.USER_EMAIL)
    }

    if ((!failureNotified || alwaysNotify) && (isDefaultBranch(this) || env.ENVIRONMENT == 'stg' || env.ENVIRONMENT == 'prd')) {
        failureNotified = true
        sendSlackMessage("[${projectName}] Pipeline Failure: ${message} ${userId} (<${env.BUILD_URL}|Jenkins>)", "red", useProdChannel())
    }
}

def useProdChannel(Boolean priorityNotification = false) {
    return env.ENVIRONMENT == 'prd' && priorityNotification ? prodSlackChannel : slackChannel
}

def getSlackUserFromEmail(String email) {
    if (email == null) {
        return ""
    }
    def userId = email.split("@")[0]
    return "@${userId}"
}

def sendSlackMessage(String message, String color, String channel = 'rxp-notifications') {
    echo message
    def messageColor = ""
    if (color == "green") {
        messageColor = "66ff33"
    }
    if (color == "red") {
        messageColor = "fc0341"
    }
    if (color == "blue") {
        messageColor = "0066ff"
    }
    slackSend channel: channel, color: messageColor, message: message
}

/**
 * Updates a comment on a GitHub pull request, deleting the previous comment if empty
 *
 * @param comment content to be posted
 * @param baseBranch base branch name (optional)
 * @param prNumber The number of the pull request; always a positive number
 * @param id unique id for the comment to be updated or deleted
 * @param githubOwner The owner of the repository (e.g., Chewy-Inc | Chewy-Int)
 * @param githubRepo The name of the repository
 *
 * @return null on success
 * @throws Exception If the API request fails
 */
def updateComment(
        String comment = '',
        String baseBranch = null,
        String prNumber = '0',
        String id = null,
        String githubOwner = null,
        String githubRepo = null
) {
    return updateComment(this, comment, baseBranch, prNumber, id, githubOwner, githubRepo)
}

// feature, master, and PR build assigned user email retrieval
def getUserEmailFromGitBuildData() {
    echo "getUserEmailFromGitBuildData"
    try {
        def email = getUserNameFromGitBuildData(env.GIT_COMMIT) + "@chewy.com"
        echo "Author email: ${email}"
        env.USER_EMAIL = email
        return email
    } catch (exception) {
        echo("getUserEmailFromGitBuildData failure")
        echo(exception.toString())
    }

    return null
}

def getUserNameFromGitBuildData(String revList) {
    return sh(returnStdout: true, script: "git log ${revList} -1 --format=%al | sed 's/[0-9]*+\\(.*\\)-chwy/\\1/'").trim()
}

// tag is provided -> used in daily automated production deployments
def getUserEmailFromTagCommit(String tag) {
    echo "getUserEmailFromTagCommit"
    try {
        withCredentials([
            usernamePassword(credentialsId: 'jenkins-github-userpass', passwordVariable: 'GRGIT_PASS', usernameVariable: 'GRGIT_USER')
        ]) {
            String email = getUserNameFromGitBuildData(tag) + "@chewy.com"
            echo("user email = ${email}")
            env.USER_EMAIL = email
            return email
        }
    } catch (exception) {
        echo("getUserEmailFromTagCommit failure")
        echo(exception.toString())
    }

    return null
}

// manually triggered jobs (manual stg and prod deployments)
def getUserEmailFromManualJenkinsJob() {
    echo "getUserEmailFromManualJenkinsJob"
    try {
        String email = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
        echo("user email = ${email}")
        env.USER_EMAIL = email
        return email
    } catch (exception) {
        echo("getUserEmailFromManualJenkinsJob failure: ${exception}")
    }

    return null
}

def getCommitMessageFromCommitHash(String hash) {
    return sh(returnStdout: true, script: "git log ${hash} -1 --format=%B").trim()
}

/**
 * Returns the Docker command line arguments to run a Docker container on a Jenkins agent.
 * This will re-mount the agent's Docker binaries, socket, etc.. on any containers started with
 * these arguments. This allows a Docker pipeline stage to reuse the authenticated Docker daemon
 * already running on the agent.
 *
 * @param additionalArgs Any additional arguments to pass to the Docker command line.
 * @return The Docker command line arguments.
 */
def getJenkinsDockerArgs(String additionalArgs = '') {
    if (additionalArgs == null) {
        additionalArgs = ''
    }
    // Obtain specifics from base operating system of Jenkins agent.
    // The use of queried values ensure the job continues to work as builder AMIs shift over time.
    String dockerClientPath = sh(script: 'which docker', returnStdout: true).trim()
    String dockerComposePath = sh(script: 'which docker-compose', returnStdout: true).trim()
    String dockerSocketGroupId = sh(script: 'stat -c %g /var/run/docker.sock', returnStdout: true).trim()
    // Volume bind the Jenkins agent (host)'s home directory into the Docker container and set env vars so that
    // shells, Gradle, and Sonarqube will use it. This brings in config and authentication for things like
    // Gradle, etc. from the host to the container.
    return "-v ${env.HOME}:${env.HOME} " +
            // Mount the Jenkins .ssh directory to the containers jenkins home directory. By default, ssh uses the
            // home directory specified in /etc/passwd, and then falls back to the $HOME environment variable.
            "-v ${env.HOME}/.ssh:/home/jenkins/.ssh " +
            "-e HOME=${env.HOME} " +
            "-e GRADLE_USER_HOME=${env.HOME}/.gradle " +
            "-e SONAR_USER_HOME=${env.HOME}/.sonar " +
            // Volume bind and establish permissions such that any Docker commands will actually invoke the host's
            // Docker engine rather than requiring that the container itself have Docker installed. This avoids the
            // nightmare of Docker-in-Docker.
            "-v /var/run/docker.sock:/var/run/docker.sock " +
            "-v ${dockerClientPath}:/usr/bin/docker " +
            "-v ${dockerComposePath}:/usr/local/bin/docker-compose " +
            // Mount /usr/libexec/docker, which is where buildx and other plugins live on our current Jenkins AMIs.
            // This may change or no longer be necessary when we upgrade to Docker > v20 on our base AMIs.
            "-v /usr/libexec/docker:/usr/libexec/docker " +
            "--group-add ${dockerSocketGroupId} ${additionalArgs}"
}

def getJenkinsDocker(Map props) {
    String label = props.label
    boolean alwaysPull = true
    String image = props.image
    String registryUrl= props.registryUrl
    String registryCredentialsId = props.registryCredentialsId
    boolean reuseNode = true
    if (props.alwaysPull != null) {
        alwaysPull = props.alwaysPull
    }
    if (props.reuseNode != null) {
        reuseNode = props.reuseNode
    }
    String dockerRunArgs = getJenkinsDockerArgs(props.additionalArgs as String)

    return [label: label, alwaysPull: alwaysPull, image: image, registryUrl: registryUrl,
        registryCredentialsId: registryCredentialsId, reuseNode: reuseNode, args: dockerRunArgs]
}

def getProxyBasePath() {
    return isDefaultBranch(this) ? "" : "/eks/ns/${k8s.generateNamespaceName()}/${env.APP_NAME}"
}
