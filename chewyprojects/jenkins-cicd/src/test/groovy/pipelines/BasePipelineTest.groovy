package pipelines

import com.lesfurets.jenkins.unit.PipelineTestHelper
import com.lesfurets.jenkins.unit.RegressionTest
import pipelines.github.Repository
import pipelines.helpers.ArtifactoryHelper
import pipelines.helpers.BoreasHelper
import pipelines.helpers.CurrentBuildHelper
import pipelines.helpers.GithubHelper
import pipelines.helpers.K8sHelper
import pipelines.helpers.ObservabilityHelper
import pipelines.helpers.PetToolsHelper
import pipelines.helpers.PipelineTestHelperInterceptor
import pipelines.helpers.SecretsHelper
import pipelines.helpers.SemverHelper

import java.util.regex.Pattern

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static com.lesfurets.jenkins.unit.global.lib.ProjectSource.projectSource

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library

import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import com.lesfurets.jenkins.unit.LibClassLoader
import org.junit.jupiter.api.BeforeEach

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class BasePipelineTest extends DeclarativePipelineTest implements RegressionTest {

    public static final String DATADOG = 'datadog'
    public static final String RELEASES = 'releases'
    public static final String GENERATE_NOTES = 'generate-notes'
    public static final String TAGS = 'tags'
    List<Map<String, String>> tagsMap
    String CHANGE_ID = '123'
    String COMMIT_HASH = '7c7ddb38cf9268fc219636b81e4189eefd650a20'
    private final List<String> regions = ['us-east-1', 'us-east-2']
    private final List<String> lowerEnvs = ['dev', 'qat', 'stg']
    private Map<String, Collection<String>> callArgs

    static Pattern s3CPPattern(String version, String env, String region) {
        Pattern.compile("aws s3 cp --quiet s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/.*/${version}/${env}/${region}/jenkins-cicd.json -")
    }

    static Pattern s3LSPattern(String version, String env, String region) {
        Pattern.compile("aws s3 ls s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/.*/${version}/${env}/${region}/jenkins-cicd.json")
    }

    @Override
    @BeforeEach
    void setUp() {
        callArgs = null
        scriptRoots += 'src/test/resources'
        scriptExtension = ''

        super.setUp()

        def proxyMetaClass = ProxyMetaClass.getInstance(PipelineTestHelper)
        proxyMetaClass.interceptor = new PipelineTestHelperInterceptor()

        helper.metaClass = proxyMetaClass
        helper.libLoader.preloadLibraryClasses = false
        helper.registerSharedLibrary(library()
                .name('jenkins-cicd')
                .defaultVersion('<notNeeded>')
                .allowOverride(true)
                .implicit(true)
                .targetPath('<notNeeded>')
                .retriever(projectSource())
                .build())
        helper.registerSharedLibrary(library()
                .name('jenkins-library')
                .retriever(projectSource())
                .targetPath('<notNeeded>')
                .defaultVersion('main')
                .allowOverride(true)
                .implicit(false)
                .build())
        helper.registerSharedLibrary(library()
                .name('innersource-jenkins-utils')
                .retriever(projectSource())
                .targetPath('<notNeeded>')
                .defaultVersion('main')
                .allowOverride(true)
                .implicit(false)
                .build())
        helper.registerSharedLibrary(library()
                .name('jenkins')
                .retriever(projectSource())
                .targetPath('<notNeeded>')
                .defaultVersion('main')
                .allowOverride(true)
                .implicit(false)
                .build())
        helper.registerSharedLibrary(library()
                .name('red-supergiant')
                .retriever(projectSource())
                .targetPath('<notNeeded>')
                .defaultVersion('main')
                .allowOverride(true)
                .implicit(false)
                .build())

        helper.registerAllowedMethod("library", [String], { String expression ->
            helper.getLibLoader().loadImplicitLibraries()
            helper.getLibLoader().loadLibrary(expression)
            helper.setGlobalVars(binding)

            return new LibClassLoader(helper, null)
        })

        helper.registerAllowedMethod("library", [Map], { Map args ->
            assert args.identifier
            helper.getLibLoader().loadImplicitLibraries()
            helper.getLibLoader().loadLibrary(args.identifier)
            helper.setGlobalVars(binding)
            return new LibClassLoader(helper, null)
        })

        helper.registerAllowedMethod('modernSCM', [LinkedHashMap.class], null)

        helper.registerAllowedMethod('checkout', [Map.class], { Map map ->
            return [GIT_COMMIT: 'GIT_COMMIT']
        })

        helper.registerAllowedMethod('ecrLogin')
        helper.registerAllowedMethod('parameters', [List.class], null)

        helper.registerAllowedMethod('withAWS', [Map.class, Closure.class], { Map map, Closure closure ->
            closure()
        })

        helper.registerAllowedMethod('s3Upload', [Map.class], { Map map -> println(map) })

        helper.registerAllowedMethod('withSonarQubeEnv', [String.class, Closure.class], { String string, Closure closure ->
            closure()
        })

        helper.registerAllowedMethod('httpRequest', [LinkedHashMap.class], {
            def content = ''
            if (it.url.contains(DATADOG)) {
                content = DATADOG
            } else if (it.url.contains('github') && it.url.contains(RELEASES)) {
                content = RELEASES
            } else if (it.url.contains('github') && it.url.contains(TAGS)) {
                content = TAGS
            }
            if (it.url.contains('github') && it.url.contains("/ref/tags/")) {
                return [content: content, status: 404]
            }
            if (it.url.contains('github') && it.url.contains("/releases/tags/")) {
                return [content: content, status: 404]
            }
            if (it.url.contains('github') && it.url.contains(GENERATE_NOTES)) {
                return [content: content, status: 200]
            }
            return [content: content, status: 201]
        })

        tagsMap = [
            [name: '1.2.3'],
            [name: '1.2.2'],
        ]

        helper.registerAllowedMethod('readJSON', [Map.class], {
            if (it.text == DATADOG) {
                return []
            }
            if (it.text == RELEASES) {
                return [body: "releaseNotes"]
            }
            if (it.text == TAGS) {
                return tagsMap
            }
            if (it.text == '{"client_id": "mock-client-id", "client_secret": "mock-dynatrace-oauth-secret"}') {
                return [client_id: "mock-client-id", client_secret: "mock-dynatrace-oauth-secret"]
            }
            if (it.text == '{"access_token": "mock-token", "token_type": "Bearer"}') {
                return [access_token: "mock-token", token_type: "Bearer"]
            }
            if (it.text == '{"results": [{"id": "workflow-123"}]}') {
                return [results: [[id: "workflow-123"]]]
            }
            if (it.text == '{"id": "execution-123", "state": "SUCCESS"}') {
                return [id: "execution-123", state: "SUCCESS"]
            }
            if (it.text == '{"state": "SUCCESS"}') {
                return [state: "SUCCESS"]
            }
            if (it.text == '{"task1":{"result":{"validation_status":"success"}},"task2":{"result":{"batch.status":"success"}}}') {
                return [
                    task1: [result: [validation_status: "success"]],
                    task2: [result: ["batch.status": "success"]],
                ]
            }
            return [[head: [sha: COMMIT_HASH]]]
        })

        helper.registerAllowedMethod('error', [String.class], { String error ->
            throw new Exception(error)
        })

        helper.registerAllowedMethod('slackSend', [Map.class], { Map map ->
            println(map.get('message'))
        })


        binding.setVariable('S3_ARTIFACTS_BUCKET', 'S3_ARTIFACTS_BUCKET')
        helper.registerAllowedMethod('s3Download', [Map.class], { Map map -> println(map) })
        helper.registerAllowedMethod('s3Upload', [Map.class], { Map map -> println(map) })
        binding.setVariable('arti', new ArtifactoryHelper())
        binding.setVariable('boreas', new BoreasHelper())
        binding.setVariable('currentBuild', new CurrentBuildHelper())
        binding.setVariable('k8s', new K8sHelper())
        binding.setVariable('observability', new ObservabilityHelper())
        binding.setVariable('petTools', new PetToolsHelper())
        binding.setVariable('secretsmanager', new SecretsHelper())
        helper.addShMock(
                "git show -s --format='%ae' unknown rollback version...1.2.3 --no-patch | awk -F'[+-]' '{print \$2}' | sort | uniq",
                "dwoo\nbbooms",
                0
                )
        mockGetLatestTagScript('1.2.3')
        // to mock lock step
        def mockSteps = new Expando()
        mockSteps.lock = { Map args, Closure body ->
            println "LOCKED: ${args.resource}"
            body()
        }
        binding.setVariable('steps', mockSteps)       // needed for top-level DSL
        binding.setVariable('github', new GithubHelper())
        binding.setVariable('semver', new SemverHelper())

        for (String env : lowerEnvs) {
            for (String region : regions) {
                helper.addShMock(s3LSPattern('1.2.3', env, region)) {
                    return [stdout: '', exitValue: 0]
                }
                helper.addShMock(s3CPPattern('1.2.3', env, region)) {
                    return [stdout: '[{"deployment":{"success": true}}]', exitValue: 0]
                }
                helper.addShMock(s3CPPattern('', env, region)) {
                    return [stdout: '[{"deployment":{"success": true}}]', exitValue: 0]
                }
            }
        }

        helper.addShMock("cat <<EOFCICD | aws s3 cp") {
            return [stdout: 'response', exitValue: 0]
        }
        helper.addShMock(Pattern.compile("(?s).*https://sso\\.dynatrace\\.com/sso/oauth2/token.*")) {
            return [stdout: '{"access_token": "mock-token", "token_type": "Bearer"}', exitValue: 0]
        }

        helper.addShMock(Pattern.compile("(?s)^(?!.*run).*/platform/automation/v1/workflows.*")) {
            return [stdout: '{"results": [{"id": "workflow-123"}]}', exitValue: 0]
        }

        helper.addShMock(Pattern.compile("(?s)(?=.*run).*/platform/automation/v1/workflows.*")) {
            return [stdout: '{"id": "workflow-123"}]', exitValue: 0]
        }

        helper.addShMock(Pattern.compile("(?s).*workflow-123.*")) {
            return [stdout: '{"id": "execution-123", "state": "SUCCESS"}', exitValue: 0]
        }

        helper.addShMock(Pattern.compile("(?s).*executions.*")) {
            return [
                stdout: '{"state": "SUCCESS"}',
                exitValue: 0,
            ]
        }

        helper.addShMock(Pattern.compile("(?s).*tasks.*")) {
            return [
                stdout: '{"task1":{"result":{"validation_status":"success"}},"task2":{"result":{"batch.status":"success"}}}',
                exitValue: 0,
            ]
        }


        addEnvVar('CHANGE_ID', CHANGE_ID)
        addEnvVar('GIT_COMMIT', COMMIT_HASH)
        addParam('TAG', PipelineConfig.LATEST_TAG)
        addParam('ACTION', PipelineConfig.DEPLOY)
    }

    void setupBranchName(String branchName) {
        addEnvVar('CHANGE_BRANCH', branchName)
        addEnvVar('BRANCH_NAME', branchName)

        Repository.metaClass.static.isDefaultBranch = { Script script ->
            return true
        }

        helper.addShMock("git rev-parse --abbrev-ref HEAD", branchName, 0)
    }

    List<String> getCallArgsForMethod(String methodName) {
        if (callArgs == null) {
            callArgs = helper.callStack
                    .groupBy { call -> call.methodName }
                    .collectEntries { entry ->
                        [(entry.key): entry.value.collect {call -> callArgsToString(call) }]
                    } as Map<String, Collection<String>>
                }
        return callArgs.getOrDefault(methodName, [])
    }

    void verifyCall(String methodName, String arguments) {
        assertTrue(getCallArgsForMethod(methodName).any { call ->
            call.contains(arguments)
        })
    }

    void verifyNotCalled(String methodName, String arguments) {
        assertFalse(getCallArgsForMethod(methodName).any { call ->
            call.contains(arguments)
        })
    }

    /**
     * Sets up GitHub mocking for tests that interact with GitHub APIs.
     * This method consolidates common GitHub mocking setup used across multiple test classes.
     */
    void setupGitHubMocking() {
        // Mock GitHub environment variables for all tests
        addEnvVar('GIT_URL', 'https://github.com/Chewy-Inc/your-repo.git')
        addEnvVar('GH_TOKEN', 'dummy-token')

        // Mock getOwner to always return your desired value
        pipelines.github.Github.metaClass.'static'.getOwner = { env, url = null -> 'Chewy-Inc' }

        // Mock Api.prependBaseUrl method
        pipelines.github.Api.metaClass.'static'.prependBaseUrl = { String apiUrl ->
            "https://api.github.com/${apiUrl}"
        }

        // Mock all GitHub API calls to prevent real HTTP requests
        pipelines.github.Api.metaClass.'static'.fetchFromGitHub = { String apiUrl, String token ->
            // Return empty response for any GitHub API calls
            return [:]
        }

        pipelines.github.Api.metaClass.'static'.postToGithub = { String apiUrl, String token, Object body ->
            // Handle both String and Map body types
            return [id: 12345, body: body]
        }

        pipelines.github.Api.metaClass.'static'.deleteFromGithub = { String apiUrl, String token ->
            // Return true for successful deletion
            return true
        }
    }

    protected void mockGetLatestTagScript(String tag) {
        helper.addShMock(
                "git tag | grep -E '^[0-9]+\\.[0-9]+\\.[0-9]+\$' | sort -Vr | head -n 1",
                tag,
                0
                )
    }
}
