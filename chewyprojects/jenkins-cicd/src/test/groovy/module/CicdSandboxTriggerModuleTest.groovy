package module

import hudson.model.Result
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.PipelineConfig
import pipelines.module.CicdSandboxTriggerModule
import pipelines.module.ModuleConfig

import static org.junit.jupiter.api.Assertions.*

class CicdSandboxTriggerModuleTest {
    private CicdSandboxTriggerModule module
    private MockJenkins jenkins
    private ModuleConfig config
    private PipelineConfig pipelineConfig

    @BeforeEach
    void setUp() {
        jenkins = new MockJenkins()
        pipelineConfig = PipelineConfig.builder()
                .jenkins(jenkins)
                .projectName('test-project')
                .build()

        config = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectName('test-project')
                .projectVersion('1.0.0')
                .env('dev')
                .build()

        // Set isPrBuild manually since builder doesn't support it
        config.isPrBuild = false
    }

    @Test
    void testConstructorWithDefaults() {
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        assertNotNull(module)
        assertEquals('ALL', module.testEnvironment)
        assertEquals('BRANCH', module.deploymentType)
        assertEquals('build-cicd-sandbox/main', module.sandboxJobPath)
    }

    @Test
    void testConstructorWithCustomValues() {
        def props = [
            testEnvironment: 'DEV',
            deploymentType: 'TAG',
            sandboxJobPath: 'custom-sandbox-job',
        ]
        module = new CicdSandboxTriggerModule(pipelineConfig, props)

        assertEquals('DEV', module.testEnvironment)
        assertEquals('TAG', module.deploymentType)
        assertEquals('custom-sandbox-job', module.sandboxJobPath)
    }

    @Test
    void testShouldTriggerSandboxForPrBuild() {
        jenkins.env.CHANGE_ID = 'PR-123'
        config.isPrBuild = true
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        assertTrue(module.shouldTriggerSandbox(config))
        assertTrue(jenkins.echoMessages.any { it.contains('Triggering cicd-sandbox for PR build: PR-123') })
    }

    @Test
    void testShouldNotTriggerSandboxForNonPrBuild() {
        config.isPrBuild = false
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        assertFalse(module.shouldTriggerSandbox(config))
        assertTrue(jenkins.echoMessages.any { it.contains('Skipping cicd-sandbox trigger - not enabled for this build type') })
    }

    @Test
    void testTriggerSandboxTestSuccess() {
        jenkins.env.CHANGE_BRANCH = 'feature/test-branch'
        jenkins.setBuildResult('SUCCESS')
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        assertTrue(module.triggerSandboxTest(config))
        assertTrue(jenkins.echoMessages.any { it.contains('Triggering sandbox job') })
        assertTrue(jenkins.echoMessages.any { it.contains('CICD Sandbox test passed successfully') })
    }

    @Test
    void testTriggerSandboxTestFailure() {
        jenkins.env.CHANGE_BRANCH = 'feature/test-branch'
        jenkins.setBuildResult('FAILURE')
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        assertFalse(module.triggerSandboxTest(config))
        assertTrue(jenkins.echoMessages.any { it.contains('CICD Sandbox test failed with result: FAILURE') })
    }

    @Test
    void testTriggerSandboxTestWithoutChangeBranch() {
        jenkins.env.CHANGE_BRANCH = null
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        assertFalse(module.triggerSandboxTest(config))
        assertTrue(jenkins.echoMessages.any { it.contains('ERROR: CHANGE_BRANCH is not set') })
    }

    @Test
    void testTriggerSandboxTestWithException() {
        jenkins.env.CHANGE_BRANCH = 'feature/test-branch'
        jenkins.throwExceptionOnBuild = true
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        assertFalse(module.triggerSandboxTest(config))
        assertTrue(jenkins.echoMessages.any { it.contains('Failed to trigger cicd-sandbox job') })
    }



    @Test
    void testCheckSandboxTestSuccess() {
        config.isPrBuild = true
        jenkins.env.CHANGE_BRANCH = 'feature/test-branch'
        jenkins.setBuildResult('SUCCESS')
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        assertTrue(module.checkSandboxTest(config))
        assertTrue(jenkins.echoMessages.any { it.contains('CICD Sandbox test validation successful') })
    }

    @Test
    void testCheckSandboxTestFailure() {
        config.isPrBuild = true
        jenkins.env.CHANGE_BRANCH = 'feature/test-branch'
        jenkins.setBuildResult('FAILURE')
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        try {
            module.checkSandboxTest(config)
            fail("Expected error() to be called, but method completed normally")
        } catch (Exception e) {
            // Expected - jenkins.error() should interrupt execution
            assertTrue(jenkins.echoMessages.any { it.contains('CICD Sandbox test failed') })
            assertEquals('FAILURE', jenkins.currentBuild.result)
        }
    }

    @Test
    void testInitializeStages() {
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        // Access validate to trigger initialization
        def validateStage = module.getValidate()
        assertNotNull(validateStage)
    }

    @Test
    void testTriggerSandboxTestWithCustomParameters() {
        jenkins.env.CHANGE_BRANCH = 'feature/test-branch'
        jenkins.setBuildResult('SUCCESS')

        def props = [
            testEnvironment: 'QAT',
            deploymentType: 'TAG',
            sandboxJobPath: 'custom-job-path',
        ]
        module = new CicdSandboxTriggerModule(pipelineConfig, props)

        assertTrue(module.triggerSandboxTest(config))

        // Verify custom parameters were used
        def lastBuildCall = jenkins.lastBuildCall
        assertNotNull(lastBuildCall)
        assertEquals('custom-job-path', lastBuildCall.job)

        def params = lastBuildCall.parameters
        assertTrue(params.any { it.name == 'TEST_ENVIRONMENT' && it.value == 'QAT' })
        assertTrue(params.any { it.name == 'DEPLOYMENT_TYPE' && it.value == 'TAG' })
    }

    @Test
    void testCheckSandboxTestWithDebugMode() {
        config.isPrBuild = true
        jenkins.env.CHANGE_BRANCH = 'feature/test-branch'
        jenkins.env.DEBUG_MODE = 'true'
        jenkins.throwExceptionOnBuild = true
        module = new CicdSandboxTriggerModule(pipelineConfig, [:])

        try {
            module.checkSandboxTest(config)
            fail("Expected error() to be called, but method completed normally")
        } catch (Exception e) {
            // Expected - jenkins.error() should interrupt execution
            assertTrue(jenkins.echoMessages.any { it.contains('Failed to trigger cicd-sandbox job') })
        }
    }

    // Mock Jenkins implementation
    class MockJenkins {
        def env = [:]
        def params = [:]
        def currentBuild = [result: null]
        List<String> echoMessages = []
        boolean throwExceptionOnBuild = false
        String buildResult = 'SUCCESS'
        Map lastBuildCall = null
        boolean errorCalled = false

        void echo(String message) {
            echoMessages.add(message)
        }

        void error(String message) {
            errorCalled = true
            echoMessages.add("ERROR: " + message)
            throw new IllegalStateException(message)
        }

        def string(Map args) {
            return [name: args.name, value: args.value]
        }

        def build(Map args) {
            lastBuildCall = args
            if (throwExceptionOnBuild) {
                throw new IllegalStateException("Build failed")
            }
            return [result: buildResult, absoluteUrl: "http://jenkins/job/test/123"]
        }

        void setBuildResult(String result) {
            this.buildResult = result
        }
    }
}
