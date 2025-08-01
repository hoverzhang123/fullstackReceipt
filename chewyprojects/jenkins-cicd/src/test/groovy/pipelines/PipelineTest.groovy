package pipelines

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.helpers.CurrentBuildHelper
import pipelines.github.Api
import pipelines.github.Auth
import pipelines.github.Repository
import pipelines.jenkins.Environment

import java.util.regex.Pattern

import static org.junit.jupiter.api.Assertions.assertThrows

class PipelineTest extends BasePipelineTest {

    String closureModuleJenkinsfile = 'src/test/resources/closure-module.Jenkinsfile'
    String ignoreChangeFreezeJenkinsfile = 'src/test/resources/ignore-change-freeze.Jenkinsfile'

    @BeforeEach
    void setupMainBranch() {
        setupBranchName('main')
        setupMocks()
    }

    @AfterEach
    void cleanup() {
        // Clean up all metaclass modifications
        GroovySystem.metaClassRegistry.removeMetaClass(Environment)
        GroovySystem.metaClassRegistry.removeMetaClass(Repository)
        GroovySystem.metaClassRegistry.removeMetaClass(Api)
        GroovySystem.metaClassRegistry.removeMetaClass(Auth)
    }

    void setupMocks() {
        // Mock env object with required environment variables
        addEnvVar('GIT_URL', 'https://github.com/test-owner/test-repo.git')
        addEnvVar('CHANGE_URL', 'https://github.com/test-owner/test-repo/pull/123')
        addEnvVar('GH_TOKEN', 'test-token')
        addEnvVar('GITHUB_TOKEN', 'test-token')
        addEnvVar('GITHUB_OWNER', 'test-owner')
        addEnvVar('GITHUB_REPO', 'test-repo')
        addEnvVar('GITHUB_CREDENTIALS_ID', 'test-creds-id')

        // Mock Environment methods
        Environment.metaClass.static.getGitUrl = { Object env ->
            return 'https://github.com/test-owner/test-repo.git'
        }

        Environment.metaClass.static.getChangeUrl = { Object env ->
            return 'https://github.com/test-owner/test-repo/pull/123'
        }

        Environment.metaClass.static.getGithubToken = { Object env ->
            return 'test-token'
        }

        Environment.metaClass.static.getValue = { Object env, String key, String defaultValue = '' ->
            switch (key) {
                case 'GIT_URL':
                    return 'https://github.com/test-owner/test-repo.git'
                case 'CHANGE_URL':
                    return 'https://github.com/test-owner/test-repo/pull/123'
                case 'GH_TOKEN':
                    return 'test-token'
                case 'GITHUB_TOKEN':
                    return 'test-token'
                case 'GITHUB_OWNER':
                    return 'test-owner'
                case 'GITHUB_REPO':
                    return 'test-repo'
                case 'GITHUB_CREDENTIALS_ID':
                    return 'test-creds-id'
                default:
                    return defaultValue ?: "mock-value-for-${key}"
            }
        }

        Environment.metaClass.static.getValue = { Object env, String[] keys, String defaultValue = '' ->
            for (String key : keys) {
                switch (key) {
                    case 'GIT_URL':
                        return 'https://github.com/test-owner/test-repo.git'
                    case 'CHANGE_URL':
                        return 'https://github.com/test-owner/test-repo/pull/123'
                    case 'GH_TOKEN':
                        return 'test-token'
                    case 'GITHUB_TOKEN':
                        return 'test-token'
                    case 'GITHUB_OWNER':
                        return 'test-owner'
                    case 'GITHUB_REPO':
                        return 'test-repo'
                    case 'GITHUB_CREDENTIALS_ID':
                        return 'test-creds-id'
                    default:
                        continue
                }
            }
            return defaultValue ?: "mock-value"
        }

        // Mock Repository methods
        Repository.metaClass.static.isDefaultBranch = { String branch ->
            return branch == 'main' || branch == 'master'
        }

        Repository.metaClass.static.getLatestReleaseTag = { String owner, String repo, String token ->
            return new pipelines.github.api.GitTag('v1.2.3', 'abc123')
        }

        Repository.metaClass.static.getLatestReleaseTag = { String owner, String repo ->
            return new pipelines.github.api.GitTag('v1.2.3', 'abc123')
        }

        Repository.metaClass.static.listTags = { String owner, String repo, String token ->
            return [
                new pipelines.github.api.GitTag('v1.2.3', 'abc123'),
                new pipelines.github.api.GitTag('v1.2.2', 'def456'),
                new pipelines.github.api.GitTag('v1.2.1', 'ghi789'),
            ]
        }

        Repository.metaClass.static.listTags = { String owner, String repo ->
            return [
                new pipelines.github.api.GitTag('v1.2.3', 'abc123'),
                new pipelines.github.api.GitTag('v1.2.2', 'def456'),
                new pipelines.github.api.GitTag('v1.2.1', 'ghi789'),
            ]
        }

        Repository.metaClass.static.getOwner = { List<String> candidateUrls ->
            return 'test-owner'
        }

        Repository.metaClass.static.getRepository = { List<String> candidateUrls ->
            return 'test-repo'
        }

        // Mock Api methods
        Api.metaClass.static.makeRequest = { String url, Map headers = [:] ->
            if (url.contains('tags')) {
                return [
                    [name: 'v1.2.3'],
                    [name: 'v1.2.2'],
                    [name: 'v1.2.1'],
                ]
            } else if (url.contains('releases')) {
                return [
                    [tag_name: 'v1.2.3', name: 'Release 1.2.3'],
                    [tag_name: 'v1.2.2', name: 'Release 1.2.2'],
                ]
            }
            return [:]
        }

        Api.metaClass.static.makeHttpRequest = { String url, Map headers = [:] ->
            if (url.contains('tags')) {
                return [
                    [name: 'v1.2.3'],
                    [name: 'v1.2.2'],
                    [name: 'v1.2.1'],
                ]
            } else if (url.contains('releases')) {
                return [
                    [tag_name: 'v1.2.3', name: 'Release 1.2.3'],
                    [tag_name: 'v1.2.2', name: 'Release 1.2.2'],
                ]
            }
            return [:]
        }

        // Mock Auth methods
        Auth.metaClass.static.withGithubAuth = { Closure closure ->
            return closure.call()
        }

        Auth.metaClass.static.withGithubCredentials = { Closure closure ->
            return closure.call()
        }
    }

    @Test
    void assertDefaultTerraformStages() {
        setupBranchName('feature/terraform/BRANCH_NAME')

        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [terraform, lambda-module-test]")
        verifyCall("echo", "Deploying modules: [terraform, lambda-module-test] to dev")
    }

    @Test
    void assertManualStagingDeploy() {
        addEnvVar('ENVIRONMENT', 'stg')
        addParam('TAG', '1.2.3')
        addParam('SKIP_DEPLOY_GATES', true)
        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
    }

    @Test
    void assertManualStagingDeploy_Empty_Metadata_SKIP_DEPLOY_GATES() {
        addEnvVar('ENVIRONMENT', 'stg')
        addParam('TAG', '1.2.3')
        addParam('SKIP_DEPLOY_GATES', true)

        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyNotCalled("echo", "Monitor checks success")
    }

    @Test
    void assertManualDevDeploy() {
        addEnvVar('ENVIRONMENT', 'dev')
        addParam('TAG', '1.2.3', true)

        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
    }

    @Test
    void assertManualQatDeploy() {
        addEnvVar('ENVIRONMENT', 'qat')
        addParam('TAG', '1.2.3', true)

        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
    }

    @Test
    void assertManualStgDeploy() {
        addEnvVar('ENVIRONMENT', 'stg')
        addParam('TAG', '1.2.3', true)

        runScript(closureModuleJenkinsfile)
        verifyCall("echo", "Preprod Pipeline")
        verifyCall("echo", "Building modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Validating modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Publishing modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] with version")

        assertJobStatusSuccess()
    }

    @Test
    void assertManualStgRollback() {
        addEnvVar('ENVIRONMENT', 'stg')
        addParam('ACTION', PipelineConfig.ROLLBACK, true)
        helper.addShMock(Pattern.compile(".*helm history .* --namespace rxp-lib-test.*"), {
            return [stdout: '5 1.2.2', exitValue: 0]
        })


        runScript(closureModuleJenkinsfile)
        verifyCall("echo", "Finding most recent successful Helm revision...")
        verifyCall("echo", "Rollback modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Rollback succeeded, (stg) rolled back to version 1.2.2 for us-east-1 in apt-shared cluster")
        verifyCall("echo", "Rollback succeeded, (stg) rolled back to version 1.2.2 for us-east-2 in apt-shared cluster")
        assertJobStatusSuccess()
    }

    @Test
    void assertManualProductionDeploy() {
        addEnvVar('ENVIRONMENT', 'prd')
        addParam('TAG', '1.2.3', true)

        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to prd")
        verifyCall("echo", "Flyway module exists... deploying now")
        verifyCall("echo", "Checking Metadata file for previous envs: [stg]")
        verifyCall("echo", "Monitor checks success")
    }

    @Test
    void assertManualProductionDeployWithCRNumber() {
        addEnvVar('ENVIRONMENT', 'prd')
        addParam('TAG', '1.2.3', true)
        addParam('CR_INC_NUMBER', 'CHG12345678', true)

        runScript(closureModuleJenkinsfile)
        assertJobStatusSuccess()
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to prd")
        verifyCall("echo", "Flyway module exists... deploying now")
        verifyCall("echo", "Checking Metadata file for previous envs: [stg]")
        verifyCall("echo", "Monitor checks success")
    }

    @Test
    void assertManualProductionDeploy_No_Metadata_File_Exists() {
        addEnvVar('ENVIRONMENT', 'prd')
        addParam('TAG', '1.2.3', true)

        helper.addShMock(s3LSPattern('1.2.3', 'stg', 'us-east-1')) {
            return [stdout: '', exitValue: 1]
        }
        assertThrows(Exception.class, {
            runScript(closureModuleJenkinsfile)
        })
        assertJobStatusFailure()
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to prd")
        verifyCall("echo", "Checking Metadata file for previous envs: [stg]")
    }

    @Test
    void assertAutomatedProductionDeploy() {
        addEnvVar('ENVIRONMENT', 'prd')

        runScript(closureModuleJenkinsfile)
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to prd")
        verifyCall("echo", "Checking Metadata file for previous envs: [stg]")
    }

    @Test
    void assertAutomatedProductionDeployLatestTag() {
        addEnvVar('ENVIRONMENT', 'prd')
        binding.setVariable('currentBuild', new CurrentBuildHelper('Latest'))
        helper.addShMock("helm history closure-module -n rxp-lib-test --kubeconfig ./kube-config-us-east-1-prd-use1-rxp-eks-cluster" +
                '| grep deployed ' +
                '| tail -1 ' +
                "| awk -F'\\t' '{print \$5}'", '1.2.3', 0)
        runScript(closureModuleJenkinsfile)
        verifyCall("echo", "previous successful build version = 1.2.3")
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to prd")
        verifyCall("echo", "Checking Metadata file for previous envs: [stg]")
        binding.setVariable('currentBuild', new CurrentBuildHelper('1.2.3'))
    }

    @Test
    void assertAutomatedProductionDeployPrefixedTags() {
        addEnvVar('ENVIRONMENT', 'prd')
        tagsMap = [
            [name: '1.2.3'],
            [name: '1.2.2'],
        ]

        runScript(closureModuleJenkinsfile)
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to prd")
        verifyCall("echo", "Checking Metadata file for previous envs: [stg]")
    }

    @Test
    void assertAutomatedProductionDeployNullTag() {
        mockGetLatestTagScript(null)
        addEnvVar('ENVIRONMENT', 'prd')
        assertThrows(Exception.class, {
            runScript(closureModuleJenkinsfile)
        })
    }

    @Test
    void assertAutomatedProductionDeployEmptySpacesTag() {
        mockGetLatestTagScript(" ")
        addEnvVar('ENVIRONMENT', 'prd')
        assertThrows(Exception.class, {
            runScript(closureModuleJenkinsfile)
        })
    }

    @Test
    void assertAutomatedProductionDeployEmptyStringTag() {
        mockGetLatestTagScript("")
        addEnvVar('ENVIRONMENT', 'prd')
        assertThrows(Exception.class, {
            runScript(closureModuleJenkinsfile)
        })
    }

    @Test
    void assertAutomatedProductionDeployNewlineTag() {
        mockGetLatestTagScript("\n")
        addEnvVar('ENVIRONMENT', 'prd')
        assertThrows(Exception.class, {
            runScript(closureModuleJenkinsfile)
        })
    }

    @Test
    void assertAutomatedProductionSkipDeploymentTagExists() {
        addEnvVar('ENVIRONMENT', 'prd')
        helper.registerAllowedMethod('httpRequest', [LinkedHashMap.class], {
            def content = ''
            if (it.url.contains(DATADOG)) {
                content = DATADOG
            } else if (it.url.contains('github') && it.url.contains(RELEASES)) {
                content = RELEASES
            } else if (it.url.contains('github') && it.url.contains(TAGS)) {
                content = TAGS
            }
            // this is only called when looking for SKIP_AUTOMATED_DEPLOYMENT_TAG
            if (it.url.contains('github') && it.url.contains("/ref/tags/")) {
                return [content: content, status: 200]
            }
            if (it.url.contains('github') && it.url.contains("/releases/tags/")) {
                return [content: content, status: 404]
            }
            if (it.url.contains('github') && it.url.contains(GENERATE_NOTES)) {
                return [content: content, status: 200]
            }
            return [content: content, status: 201]
        })
        assertThrows(Exception.class, {
            runScript(closureModuleJenkinsfile)
        })
        verifyCall("echo", "Skipping automated deployment due to skip-automated-deployments tag")
    }

    @Test
    void assertAutomatedProductionDeployRollback() {
        addEnvVar('ENVIRONMENT', 'prd')
        addParam('ACTION', PipelineConfig.ROLLBACK, true)

        helper.addShMock("aws s3 ls s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/jenkins-cicd-tests/1.2.3/stg/us-east-1/jenkins-cicd.json") {
            return [stdout: '', exitValue: 0]
        }
        helper.addShMock(Pattern.compile(".*helm history .*"), {
            return [stdout: '5 1.2.2', exitValue: 0]
        })

        runScript(closureModuleJenkinsfile)
        verifyCall("echo", "Finding most recent successful Helm revision...")
        verifyCall("echo", "Rollback modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test]")
        verifyCall("echo", "Rollback succeeded, (prd) rolled back to version 1.2.2 for us-east-1 in rxp-eks-cluster")
        verifyCall("echo", "Rollback succeeded, (prd) rolled back to version 1.2.2 for us-east-2 in rxp-eks-cluster")
        verifyCall("echo", "Rollback succeeded, (prd) rolled back to version 1.2.2 for us-east-1 in apt-shared")
        verifyCall("echo", "Rollback succeeded, (prd) rolled back to version 1.2.2 for us-east-2 in apt-shared")
    }

    @Test
    void assertAutomatedProductionDeployNoChangeFreezeChecks() {
        addEnvVar('ENVIRONMENT', 'prd')


        runScript(ignoreChangeFreezeJenkinsfile)
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor] to prd")
        verifyCall("echo", "Checking Metadata file for previous envs: [stg]")
        verifyNotCalled("echo", "Checking for change freeze in prd")
    }

    @Test
    void assertAutomatedProductionDeployFailure() {
        addEnvVar('ENVIRONMENT', 'prd')
        addParam('TAG', '1.2.3', true)

        helper.addShMock(s3LSPattern('1.2.3', 'stg', 'us-east-1')) {
            return [stdout: '', exitValue: 1]
        }
        assertThrows(Exception.class, {
            runScript(closureModuleJenkinsfile)
        })
        assertJobStatusFailure()
        verifyCall("echo", "Deploying modules: [app (DockerModule), helm, datadog monitor, aptApp (AptDockerModule), aptHelm, datadog monitor, terraform, flyway, e2e, libraries, test-closure-module, lambda-module-test] to prd")
        verifyCall("echo", "Checking Metadata file for previous envs: [stg]")
    }
}
