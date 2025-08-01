package module

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.module.HelmChartModule
import pipelines.module.ModuleConfig
import pipelines.module.stage.HelmReleaseStage
import pipelines.module.stage.HelmRollbackStage

import static org.junit.jupiter.api.Assertions.*

class HelmChartModuleTest {
    static final String TEST_ACCOUNT_ID = "123456789012"
    static final String WORKSPACE_PATH = "/tmp"
    static final String TEST_REGION = "us-east-1"
    static final String DEPLOY_TIMEOUT = "5m0s"
    static final String TEST_CHART_DIRECTORY = "/tmp/mychart"

    MockJenkins jenkins
    HelmChartModule module

    @BeforeEach
    void setUp() {
        jenkins = new MockJenkins()
        jenkins.env.WORKSPACE = WORKSPACE_PATH
        jenkins.tarballExists = true
        jenkins.arti = new MockArti()
        module = new HelmChartModule(WORKSPACE_PATH, DEPLOY_TIMEOUT)

        module.regionDeploy = new HelmReleaseStage() {
                    def runner = { println "Mocked runner for regionDeploy" }
                }

        module.regionRollback = new HelmRollbackStage() {
                    def runner = { println "Mocked runner for regionRollback" }
                }
    }

    @Test
    void testBuildChartRunsCommands() {
        def config = new MockConfig(jenkins: jenkins)
        module.packageChart(config)
        assertTrue(jenkins.shCalled)
    }

    @Test
    void testPublishChartCallsBuildChartIfPreviousVersion() {
        def config = new MockConfig(jenkins: jenkins, previousVersion: "0.9.0")
        module.publishChart(config)
        assertTrue(jenkins.arti.publishCalled)
    }

    @Test
    void testPublishChartWithoutPreviousVersion() {
        def config = new MockConfig(jenkins: jenkins, previousVersion: null)
        module.publishChart(config)
        assertTrue(jenkins.arti.publishCalled)
    }

    @Test
    void testPullChartDownloadsWhenNotExists() {
        jenkins.env.WORKSPACE = WORKSPACE_PATH
        jenkins.tarballExists = false
        def config = new MockConfig(jenkins: jenkins)
        module.pullChart(config)
        assertTrue(jenkins.arti.pullCalled)
    }

    @Test
    void testPackageChartFailsWithInvalidDirectory() {
        // Simulate missing Chart.yaml
        jenkins.sh = { arg ->
            if (arg instanceof Map && arg.returnStdout && arg.script?.contains("Chart.yaml")) {
                return "no"
            }
            return "yes"
        }
        def config = new MockConfig(jenkins: jenkins)
        assertThrows(Exception) {
            module.packageChart(config)
        }
    }

    @Test
    void testPublishChartThrowsIfTarballMissing() {
        jenkins.env.WORKSPACE = WORKSPACE_PATH
        jenkins.tarballExists = false // Simulate tarball missing
        def config = new MockConfig(jenkins: jenkins)
        assert assertThrows(Exception) {
            module.publishChart(config)
        }.message.contains("does not exist")
    }

    @Test
    void testPullChartThrowsIfInvalidDirectory() {
        jenkins.env.WORKSPACE = WORKSPACE_PATH
        jenkins.tarballExists = false
        // Simulate invalid chart directory
        simulateMissingChartYaml()
        def config = new MockConfig(jenkins: jenkins)

        assert assertThrows(Exception) {
            module.pullChart(config)
        }.message.toLowerCase().contains("invalid")
    }

    @Test
    void testPackageChartThrowsIfHelmPackageFails() {
        jenkins.arti = new MockArti() {
                    @Override
                    def helmPackage(String dir, String appVersion, String chartVersion) {
                        throw new IllegalStateException("helm package failed")
                    }
                }
        def config = new MockConfig(jenkins: jenkins)
        assert assertThrows(IllegalStateException) {
            module.packageChart(config)
        }.message.contains("helm package failed")
    }

    @Test
    void testPackageChartWithNullProjectName() {
        def config = new MockConfig(jenkins: jenkins, projectName: null)
        // Should not throw
        module.packageChart(config)
        assertTrue(jenkins.shCalled)
    }

    @Test
    void testPublishChartWithBranchDeploy() {
        jenkins.env.WORKSPACE = WORKSPACE_PATH
        jenkins.tarballExists = true
        def config = new MockConfig(jenkins: jenkins, isBranchDeploy: true)
        module.publishChart(config)
        // If publish is skipped for branch deploys, expect false:
        assertFalse(jenkins.arti.publishCalled)
    }

    @Test
    void testGetChartNameThrowsIfMissingName() {
        def jenkins = new MockJenkins() {
                    @Override
                    def readYaml(Map args) {
                        println "readYaml called with: ${args}"
                        return [:] // Simulate missing name
                    }
                }
        jenkins.env.WORKSPACE = WORKSPACE_PATH
        jenkins.tarballExists = true // So publishChart doesn't fail early
        def config = new MockConfig(jenkins: jenkins)
        def module = new HelmChartModule(WORKSPACE_PATH, DEPLOY_TIMEOUT)
        assert assertThrows(Exception) {
            module.publishChart(config)
        }.message.contains("does not contain a 'name' field")
    }

    @Test
    void testGetChartTarBallFileReturnsCorrectPath() {
        def jenkins = new MockJenkins()
        jenkins.env.WORKSPACE = WORKSPACE_PATH
        jenkins.tarballExists = true
        def arti = new MockArti() {
                    @Override
                    def publish(String path, String version) {
                        assert path.contains("/tmp/artifacts/helm/svc-2.1.0.tgz")
                        publishCalled = true
                    }
                }
        jenkins.arti = arti
        def config = new MockConfig(jenkins: jenkins, projectVersion: "2.1.0")
        def module = new HelmChartModule(WORKSPACE_PATH, DEPLOY_TIMEOUT)
        module.publishChart(config)
        assertTrue(arti.publishCalled)
    }

    @Test
    void testValidateChartDirectoryThrowsIfInvalid() {
        // Simulate missing Chart.yaml
        simulateMissingChartYaml()
        def config = new MockConfig(jenkins: jenkins)
        assertThrows(Exception) {
            module.packageChart(config)
        }
        assertThrows(Exception) { module.pullChart(config) }
        .message
        .toLowerCase()
        .contains("invalid")
    }

    @Test
    void testPackageChartIdempotency() {
        def config = new MockConfig(jenkins: jenkins)
        def module = new HelmChartModule(WORKSPACE_PATH, DEPLOY_TIMEOUT)
        module.packageChart(config)
        module.packageChart(config)
        assertTrue(jenkins.shCalled)
    }

    @Test
    void testPackageChartWithNullProjectVersion() {
        def config = new MockConfig(jenkins: jenkins, projectVersion: null)
        def module = new HelmChartModule(WORKSPACE_PATH, DEPLOY_TIMEOUT)
        // Should not throw if code is robust, or should throw a clear error
        module.packageChart(config)
        assertTrue(jenkins.shCalled)
    }

    @Test
    void testPackageChartThrowsIfArtiIsNull() {
        def jenkins = new MockJenkins()
        jenkins.arti = null
        def config = new MockConfig(jenkins: jenkins)
        def module = new HelmChartModule(WORKSPACE_PATH, DEPLOY_TIMEOUT)
        assertThrows(Exception) {
            module.packageChart(config)
        }
    }

    @Test
    void testValidateChartLogsMessage() {
        def echoed = [false]
        def jenkins = new MockJenkins()
        jenkins.arti = new MockArti()
        jenkins.metaClass.echo = { msg ->
            if (msg.contains("No helm chart validations currently exist")) {
                echoed[0] = true
            }
        }
        def config = new MockConfig(jenkins: jenkins)
        def module = new HelmChartModule(WORKSPACE_PATH, DEPLOY_TIMEOUT)
        module.validateChart(config)
        assertTrue(echoed[0])
    }

    @Test
    void testInitializeStagesSetsRunners() {
        module.initializeStages()
        assertNotNull(module.build.runner, "Build stage runner should not be null")
        assertNotNull(module.validate.runner, "Validate stage runner should not be null")
        assertNotNull(module.publish.runner, "Publish stage runner should not be null")
        assertNotNull(module.globalDeploy.runner, "Global deploy stage runner should not be null")
    }

    @Test
    void testBuilderCreatesHelmChartModule() {
        def module = HelmChartModule.builder()
                .chartDirectory(TEST_CHART_DIRECTORY)
                .deploymentTimeout("10m0s")
                .build()

        assert module.chartDirectory.path == TEST_CHART_DIRECTORY
        assert module.deploymentTimeout == "10m0s"
        assert module.chartYamlFile.path.endsWith("Chart.yaml")
    }

    private void simulateMissingChartYaml() {
        jenkins.sh = { arg ->
            if (arg instanceof Map && arg.returnStdout && arg.script?.contains("Chart.yaml")) {
                return "no"
            }
            return "yes"
        }
    }

    private static class MockJenkins {
        def env = [:]
        def ACCOUNTS = [shd: TEST_ACCOUNT_ID]
        def REGIONS = [us: TEST_REGION]
        def k8s = [generateNamespaceName: { -> "default-ns" }]
        def fileExistsCalled = false
        def sleepCalled = false
        def shCalled = false
        def ecrLoginCalled = false
        def DEPLOY_STEP
        boolean tarballExists = false
        def fileExists(String path) {
            println "fileExists called with: $path"
            fileExistsCalled = true
            false
        }
        def sleep(int seconds) {
            sleepCalled = true
        }

        def sh = { arg ->
            shCalled = true
            if (arg instanceof Map && arg.returnStdout) {
                def script = arg.script
                println "sh called with script: $script"
                if (script?.contains("if [ -d") && script?.contains("Chart.yaml")) {
                    return "yes"
                }
                if (script?.contains("[ -f") && script?.contains(".tgz")) {
                    return tarballExists ? "yes" : "no"
                }
                return "yes"
            }
            return null
        }
        def workspace = WORKSPACE_PATH

        def readYaml(Map args) {
            if (args.file?.endsWith("Chart.yaml")) {
                return [name: "svc"]
            }
            return [:]
        }

        def arti

        def withAWS(Map args, Closure c) {
            c()
        }

        def echo(msg) {
            println msg
        }

        def notify = { a, b, c -> }
    }

    private static class MockConfig extends ModuleConfig {
        MockConfig(Map args = [:]) {
            super(
            args.jenkins ?: new Object(),
            args.projectName ?: "testproj",
            args.projectVersion ?: "1.0.0",
            "", "", ""
            )
            if (args.jenkins) {
                this.jenkins = args.jenkins
            }
            if (args.projectName) {
                this.projectName = args.projectName
            }
            if (args.projectVersion) {
                this.projectVersion = args.projectVersion
            }
            if (args.isBranchDeploy != null) {
                this.isBranchDeploy = args.isBranchDeploy
            }
            if (args.previousVersion) {
                this.previousVersion = args.previousVersion
            }
            if (args.rollbackVersion) {
                this.rollbackVersion = args.rollbackVersion
            }
        }
        def jenkins
        String projectName = "testproj"
        String projectVersion = "1.0.0"
        Boolean isBranchDeploy = false
        String previousVersion = null
        String rollbackVersion = null
    }

    private static class MockArti {
        boolean publishCalled = false
        boolean pullCalled = false
        boolean packageCalled = false
        def helmPackage(String dir, String appVersion, String chartVersion) {
            packageCalled = true
        }
        def publish(String path, String version) {
            publishCalled = true
        }
        def pull(platform, artifactName, versionNumber, explode) {
            println "MockArti.pull called"
            pullCalled = true
        }
    }
}
