package pipelines

import org.junit.jupiter.api.Test

class PreprodPipelineTest extends BasePipelineTest {

    String closureModuleJenkinsfile = 'src/test/resources/closure-module.Jenkinsfile'
    String ignoreChangeFreezeJenkinsfile = 'src/test/resources/ignore-change-freeze.Jenkinsfile'

    void setupPreprodPipeline(String branchName) {
        setupBranchName(branchName)
        addEnvVar('ENVIRONMENT', null)
        addParam('TAG', 'v1.2.3', true)
        addParam('RESPECT_CHANGE_FREEZE', true)

        helper.addShMock("gradlew tasks --all | grep -q 'artifactoryDeploy'", null, 1)
    }

    @Test
    void assertPreprodDeploy() {
        setupPreprodPipeline('main')

        runScript(closureModuleJenkinsfile)

        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall('sh', './gradlew -PprojectVersion=1.2.3 -Prelease.version=1.2.3 app:dockerBuildImage -x check')
        verifyCall('sh', './gradlew -PprojectVersion=1.2.3 -Prelease.version=1.2.3 aptApp:dockerBuildImage -x check')
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to dev")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to qat")
        verifyCall("echo", "Checking Metadata file for previous envs: [dev, qat]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to stg")
        verifyCall("echo", "AptKubernetesRegionDeployStage: namespace = pet-health")
        verifyCall("echo", "Flyway module exists... deploying now")
        verifyCall("echo", "Datadog Monitor checks success")
        verifyCall("echo", "Checking for change freeze in stg")
    }

    @Test
    void assertPreprodDeployModuleInit() {
        setupPreprodPipeline('main')

        runScript('src/test/resources/module-init.Jenkinsfile')

        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog, aptApp (AptDockerModule), dynatrace, aptHelm, datadog, terraform, flyway, e2e, libraries, spec, test-closure-module, lambda-module-test]")
        verifyCall('sh', './gradlew -PprojectVersion=1.2.3 -Prelease.version=1.2.3 app:dockerBuildImage -x check')
        verifyCall('sh', './gradlew -PprojectVersion=1.2.3 -Prelease.version=1.2.3 aptApp:dockerBuildImage -x check')
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog, aptApp (AptDockerModule), dynatrace, aptHelm, datadog, terraform, flyway, e2e, libraries, spec, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), helm, datadog, aptApp (AptDockerModule), dynatrace, aptHelm, datadog, terraform, flyway, e2e, libraries, spec, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog, aptApp (AptDockerModule), dynatrace, aptHelm, datadog, terraform, flyway, e2e, libraries, spec, test-closure-module, lambda-module-test] to dev")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog, aptApp (AptDockerModule), dynatrace, aptHelm, datadog, terraform, flyway, e2e, libraries, spec, test-closure-module, lambda-module-test] to qat")
        verifyCall("echo", "Checking Metadata file for previous envs: [dev, qat]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog, aptApp (AptDockerModule), dynatrace, aptHelm, datadog, terraform, flyway, e2e, libraries, spec, test-closure-module, lambda-module-test] to stg")
        verifyCall("echo", "AptKubernetesRegionDeployStage: namespace = pet-health")
        verifyCall("echo", "Flyway module exists... deploying now")
        verifyCall("echo", "Datadog Monitor checks success")
        verifyCall("echo", "Checking for change freeze in stg")
    }

    @Test
    void assertPreprodDeployNullAptAppModuleName() {
        setupPreprodPipeline('main')

        runScript('src/test/resources/null-apt-app-module-name.Jenkinsfile')

        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [AptDockerModule, aptHelm, datadog monitor]")
        verifyCall('sh', './gradlew -PprojectVersion=1.2.3 -Prelease.version=1.2.3 dockerBuildImage -x check')
        verifyCall("echo", "Validating modules: [AptDockerModule, aptHelm, datadog monitor]")
        verifyCall("echo", "Publishing modules: [AptDockerModule, aptHelm, datadog monitor]")
        verifyCall("echo", "Deploying modules: [AptDockerModule, aptHelm, datadog monitor] to dev")
        verifyCall("echo", "Checking Metadata file for previous envs: [dev]")
        verifyCall("echo", "Deploying modules: [AptDockerModule, aptHelm, datadog monitor] to qat")
        verifyCall("echo", "Checking Metadata file for previous envs: [qat]")
        verifyCall("echo", "Deploying modules: [AptDockerModule, aptHelm, datadog monitor] to stg")
        verifyCall("echo", "AptKubernetesRegionDeployStage: namespace = pet-health")
        verifyCall("echo", "Datadog Monitor checks success")
        verifyCall("echo", "Checking for change freeze in stg")
    }

    @Test
    void assertFeatureDeploymentPreprodDeploy() {
        setupPreprodPipeline('feature/deployment/test-feature-deployment')

        runScript(closureModuleJenkinsfile)

        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to dev")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to qat")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to stg")
        verifyCall("echo", "AptKubernetesRegionDeployStage: namespace = pet-health")
        verifyCall("echo", "Flyway module exists... deploying now")
        verifyCall("echo", "Checking Metadata file for previous envs: [dev, qat]")
        verifyCall("echo", "Datadog Monitor checks success")
        verifyCall("echo", "Checking for change freeze in stg")
    }

    @Test
    void assertHotfixPreprodDeploy() {
        setupPreprodPipeline('hotfix/test-hotfix')

        runScript(closureModuleJenkinsfile)

        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to dev")
        verifyCall("echo", "Skipping post check for dev: skipDeploymentGates flag is on")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to qat")
        verifyCall("echo", "Skipping post check for qat: skipDeploymentGates flag is on")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to stg")
        verifyCall("echo", "Skipping post check for stg: skipDeploymentGates flag is on")
        verifyCall("echo", "Flyway module exists... deploying now")
        verifyNotCalled("echo", "Checking for change freeze in stg")
    }

    @Test
    void assertALLEnvironmentPreprodDeploy() {
        setupPreprodPipeline('main')
        addParam('ENVIRONMENT', 'ALL')

        runScript(closureModuleJenkinsfile)

        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to dev")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to qat")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to stg")
        verifyCall("echo", "AptKubernetesRegionDeployStage: namespace = pet-health")
        verifyCall("echo", "Flyway module exists... deploying now")
        verifyCall("echo", "Checking Metadata file for previous envs: [dev, qat]")
        verifyCall("echo", "Datadog Monitor checks success")
        verifyCall("echo", "Checking for change freeze in stg")
    }

    @Test
    void assertQATPreprodDeploy() {
        setupPreprodPipeline('main')
        addParam('ENVIRONMENT', 'qat')

        runScript(closureModuleJenkinsfile)

        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to qat")
        verifyCall("echo", "AptKubernetesRegionDeployStage: namespace = pet-health")
        verifyCall("echo", "Flyway module exists... deploying now")
        verifyCall("echo", "Datadog Monitor checks success")
        verifyCall("echo", "Checking Metadata file for previous envs: []")
        verifyNotCalled("echo", "Checking Metadata file for previous envs: [dev]")
    }

    @Test
    void assertPreprodDeploySkipSTGChangeFreeze() {
        setupPreprodPipeline('main')
        addParam("RESPECT_CHANGE_FREEZE", false)

        runScript(ignoreChangeFreezeJenkinsfile)

        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), helm, datadog monitor]")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor] to dev")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor] to qat")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor] to stg")
        verifyCall("echo", "Checking Metadata file for previous envs: [dev, qat]")
        verifyCall("echo", "Datadog Monitor checks success")
        verifyNotCalled("echo", "Checking for change freeze in stg")
    }
}
