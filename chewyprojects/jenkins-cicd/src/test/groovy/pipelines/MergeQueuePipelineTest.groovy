package pipelines

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import pipelines.github.Api
import pipelines.github.Auth
import pipelines.github.Repository
import pipelines.jenkins.Environment

/**
 * Unit tests for the MergeQueuePipeline
 */
class MergeQueuePipelineTest extends BasePipelineTest {

    static final String MERGE_QUEUE_BRANCH = "gh-readonly-queue/main/pr-123-commithash"
    String closureModuleJenkinsfile = 'src/test/resources/closure-module.Jenkinsfile'

    @BeforeEach
    void setupFeatureBranch() {
        setupBranchName(MERGE_QUEUE_BRANCH)
        setupGitHubMocking()

        // Mock Environment methods
        Environment.metaClass.static.getGitUrl = { env -> 'https://github.com/Chewy-Inc/jenkins-cicd.git' }
        Environment.metaClass.static.getChangeUrl = { env -> 'https://github.com/Chewy-Inc/jenkins-cicd/pull/123' }
        Environment.metaClass.static.getGitBranch = { env -> MERGE_QUEUE_BRANCH }
        Environment.metaClass.static.getBranchName = { env -> MERGE_QUEUE_BRANCH }
        Environment.metaClass.static.getChangeBranch = { env -> null }
        Environment.metaClass.static.getGithubToken = { env -> 'dummy-token' }

        // Mock Repository methods with proper static method overrides
        Repository.metaClass.static.isDefaultBranch = { Script script -> false }
        Repository.metaClass.static.getDefaultBranch = { String owner, String repo, String token -> 'main' }
        Repository.metaClass.static.getOwner = { List<String> urls -> 'Chewy-Inc' }
        Repository.metaClass.static.getRepository = { List<String> urls -> 'jenkins-cicd' }
        Repository.metaClass.static.getLatestReleaseTag = { String owner, String repo, String token ->
            return new pipelines.github.api.GitTag('v1.2.3', 'abc123')
        }
        Repository.metaClass.static.listTags = { String owner, String repo, String token ->
            return [
                new pipelines.github.api.GitTag('v1.2.3', 'abc123'),
                new pipelines.github.api.GitTag('v1.2.2', 'def456'),
                new pipelines.github.api.GitTag('v1.2.1', 'ghi789'),
            ]
        }
        Repository.metaClass.static.getLatestTag = { String owner, String repo, String token ->
            return new pipelines.github.api.GitTag('v1.2.3', 'abc123')
        }

        // Mock Auth methods
        Auth.metaClass.static.withGithubAuth = { Script script, Closure closure ->
            return closure.call()
        }

        // Mock API methods
        Api.metaClass.static.get = { String url, String token ->
            return [disconnect: {}] as HttpURLConnection
        }
        Api.metaClass.static.toJson = { HttpURLConnection conn ->
            return [default_branch: 'main']
        }

        // Mock Jenkins methods
        helper.registerAllowedMethod('withCredentials', [List.class, Closure.class], { List creds, Closure closure ->
            return closure.call()
        })

        helper.registerAllowedMethod('notifyPRMergeStatusForMergeQueue', [Map.class], { Map map -> println(map) })
    }

    @AfterEach
    void cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Repository)
        GroovySystem.metaClassRegistry.removeMetaClass(Api)
        GroovySystem.metaClassRegistry.removeMetaClass(Auth)
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
        verifyCall("echo", "Uninstall helm chart: dev-use1-apt-shared")
        verifyCall("echo", "Uninstall helm chart: dev-use1-rxp-eks-cluster")
    }
}
