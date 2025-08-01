package pipelines

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import pipelines.github.Api
import pipelines.github.Auth
import pipelines.github.Repository
import pipelines.github.PullRequest
import pipelines.jenkins.Environment

class BranchPipelineTest extends BasePipelineTest {

    private static final String FEATURE_BRANCH = 'feature/BRANCH_NAME'
    private static final String closureModuleJenkinsfile = 'src/test/resources/closure-module.Jenkinsfile'
    private static final String featureBranchJenkinsfile = 'src/test/resources/feature-branch.Jenkinsfile'

    @BeforeEach
    void setupFeatureBranch() {
        setupBranchName(FEATURE_BRANCH)

        // mock Environment methods
        Environment.metaClass.static.getGitUrl = { env -> 'https://github.com/my-org/my-repo.git' }
        Environment.metaClass.static.getChangeUrl = { env -> 'https://github.com/my-org/my-repo/pull/123' }
        Environment.metaClass.static.getGitBranch = { env -> 'feature/BRANCH_NAME' }
        Environment.metaClass.static.getBranchName = { env -> 'feature/BRANCH_NAME' }
        Environment.metaClass.static.getChangeBranch = { env -> null }
        Environment.metaClass.static.getGithubToken = { env -> 'dummy-token' }

        // mock Repository methods with proper static method overrides
        Repository.metaClass.static.isDefaultBranch = { Script script -> false }
        Repository.metaClass.static.getDefaultBranch = { String owner, String repo, String token -> 'main' }
        Repository.metaClass.static.getOwner = { List<String> urls -> 'my-org' }
        Repository.metaClass.static.getRepository = { List<String> urls -> 'my-repo' }
        Repository.metaClass.static.getLatestReleaseTag = { String owner, String repo, String token -> null }
        Repository.metaClass.static.listTags = { String owner, String repo, String token -> [] }
        Repository.metaClass.static.getLatestTag = { String owner, String repo, String token -> null }

        // mock Auth methods
        Auth.metaClass.static.withGithubAuth = { Script script, Closure closure ->
            return closure.call()
        }

        // mock API methods
        Api.metaClass.static.get = { String url, String token ->
            return [disconnect: {}] as HttpURLConnection
        }
        Api.metaClass.static.toJson = { HttpURLConnection conn ->
            return [default_branch: 'main']
        }

        // mock Repository guardInput method
        Repository.metaClass.static.guardInput = { String owner, String repo, String token -> }

        // mock Jenkins methods - withCredentials only, let sh mocks be test-specific
        helper.registerAllowedMethod('withCredentials', [List.class, Closure.class], { List creds, Closure closure ->
            return closure.call()
        })
    }

    @AfterEach
    void cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Repository)
        GroovySystem.metaClassRegistry.removeMetaClass(Api)
        GroovySystem.metaClassRegistry.removeMetaClass(Auth)
        GroovySystem.metaClassRegistry.removeMetaClass(PullRequest)
        try {
            GroovySystem.metaClassRegistry.removeMetaClass(Environment)
        } catch (Exception ignored) {}
    }

    @Test
    void assertDefaultStages() {
        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyCall("echo", "Running init stage for defaultStages")
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), aptApp (AptDockerModule), lambda-module-test, test-closure-module, libraries] with version")
        verifyCall("echo", "ModuleConfig vertical: hlth")
        verifyCall("echo", "Deploying modules: [helm, aptHelm, test-closure-module, e2e] to dev")
    }

    @Test
    void assertDefaultStagesShouldNotUninstall() {
        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyNotCalled("echo", "KubernetesRegionPostDeployStage")
        verifyNotCalled("echo", "Uninstall")
    }

    @Test
    void shouldRunQATEnvironment() {
        addParam('ENVIRONMENT', 'qat')
        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyCall("echo", "Running init stage for defaultStages")
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), aptApp (AptDockerModule), lambda-module-test, test-closure-module, libraries] with version")
        verifyCall("echo", "ModuleConfig vertical: hlth")
        verifyCall("echo", "Deploying modules: [helm, aptHelm, test-closure-module, e2e] to qat")
    }

    @Test
    void assertClosureModule() {
        addParam('SKIP_DEPLOY_GATES', false)

        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyCall("echo", "Running build")
        verifyCall("echo", "Running validate")
        verifyCall("echo", "Running publish")
        verifyCall("echo", "Running preCheck")
        verifyCall("echo", "Running postCheck")
        verifyCall("echo", "Running regionDeploy")
        verifyNotCalled("echo", "Running regionRollback")
    }

    @Test
    void assertBranchBuildDisabled() {
        addEnvVar('CHANGE_ID', null)

        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyNotCalled("echo", "Building modules:")
        verifyNotCalled("echo", "Validating modules:")
        verifyNotCalled("echo", "Publishing modules:")
        verifyNotCalled("echo", "Deploying modules:")
    }

    @Test
    void assertEnabledFeatureBranchBuild() {
        addEnvVar('CHANGE_ID', null)

        runScript(featureBranchJenkinsfile)
        assertJobStatusSuccess()
        verifyFeatureBranchModulesAndEnvironments()
    }

    @Test
    void assertEnabledFeatureBranchBuildForPR() {

        runScript(featureBranchJenkinsfile)
        assertJobStatusSuccess()
        verifyFeatureBranchModulesAndEnvironments()
    }

    @Test
    void shouldDefaultEnvironmentsForALL() {
        addParam('ENVIRONMENT', 'ALL')
        runScript(featureBranchJenkinsfile)
        assertJobStatusSuccess()
        verifyFeatureBranchModulesAndEnvironments()
    }

    @Test
    void shouldDefaultEnvironmentsForInvalidBranch() {
        addParam('ENVIRONMENT', 'INVALID_ENVIRONMENT')
        runScript(featureBranchJenkinsfile)
        assertJobStatusSuccess()
        verifyFeatureBranchModulesAndEnvironments()
    }

    @Test
    void shouldRunFeatureBranchQATEnvironment() {
        addParam('ENVIRONMENT', 'qat')
        runScript(featureBranchJenkinsfile)
        assertJobStatusSuccess()
        verifyFeatureBranchModulesAndEnvironments(['qat'])
    }

    void verifyFeatureBranchModulesAndEnvironments(List<String> environments = ['dev', 'stg']) {
        verifyFeatureBranchBuildValidatePublish()
        environments.each(this.&verifyFeatureBranchDeployEnvironment)
    }

    void verifyFeatureBranchDeployEnvironment(String env) {
        verifyCall("echo", "Deploying modules: [helm, aptHelm] to " + env)
    }

    void verifyFeatureBranchBuildValidatePublish() {
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, app (AptDockerModule), aptHelm, datadog monitor]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, app (AptDockerModule), aptHelm, datadog monitor]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), app (AptDockerModule)]")
    }

    @Test
    void assertSkipCICommitSkips() {
        PipelineConfig.SKIP_CI_STRINGS.each({ skipCiString ->
            helper.addShMock(
                    "git log ${COMMIT_HASH} -1 --format=%B",
                    "test ${skipCiString}",
                    0
                    )
            runScript(featureBranchJenkinsfile)
            assertJobStatusSuccess()
            verifyCall("echo", "Skipping CI due to commit message.")
        })
    }
}
