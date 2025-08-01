package module

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.PipelineConfig
import pipelines.module.BuildModule
import pipelines.module.stage.BuildEnvironmentStage
import pipelines.module.stage.PublishDockerStage
import pipelines.module.stage.ValidateEnvironmentStage

import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for BuildModule
 * Verifies the behavior of BuildModule initialization and configuration
 */
class BuildModuleTest {

    private BuildModule buildModule
    private MockJenkins jenkins
    private PipelineConfig pipelineConfig

    @BeforeEach
    void setUp() {
        jenkins = new MockJenkins()
        pipelineConfig = PipelineConfig.builder()
                .jenkins(jenkins)
                .projectName('test-project')
                .build()
    }

    @Test
    void testConstructorWithDefaults() {
        buildModule = new BuildModule(pipelineConfig, [:])

        assertNotNull(buildModule)
        assertNotNull(buildModule.props)

        // Verify default buildEnvironment configuration
        def buildEnvironment = buildModule.props.buildEnvironment
        assertNotNull(buildEnvironment)
        assertEquals('gradle', buildEnvironment.tool)
        assertNotNull(buildEnvironment.actions)

        def buildAction = buildEnvironment.actions.build
        assertNotNull(buildAction)
        assertEquals('dockerBuildImage -x check', buildAction.task)
    }

    @Test
    void testConstructorWithCustomProps() {
        def customProps = [
            name: 'custom-module',
            ecrRepo: 'custom-repo',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [task: 'customBuild'],
                    publish: [task: 'customPublish'],
                ],
            ],
        ]

        buildModule = new BuildModule(pipelineConfig, customProps)

        assertNotNull(buildModule)
        assertEquals('custom-module', buildModule.props.name)
        assertEquals('custom-repo', buildModule.props.ecrRepo)

        def buildEnvironment = buildModule.props.buildEnvironment
        assertEquals('gradle', buildEnvironment.tool)
        assertEquals('customBuild', buildEnvironment.actions.build.task)
        assertEquals('customPublish', buildEnvironment.actions.publish.task)
    }

    @Test
    void testPropsOverride() {
        def originalProps = [
            buildEnvironment: [
                tool: 'maven',
                actions: [
                    build: [task: 'originalTask'],
                ],
            ],
        ]

        def overrideProps = [
            name: 'override-module',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [task: 'overrideTask'],
                ],
            ],
        ]

        buildModule = new BuildModule(pipelineConfig, originalProps + overrideProps)

        // Custom props should override defaults
        assertEquals('override-module', buildModule.props.name)
        assertEquals('gradle', buildModule.props.buildEnvironment.tool)
        assertEquals('overrideTask', buildModule.props.buildEnvironment.actions.build.task)
    }

    @Test
    void testStageInitialization() {
        buildModule = new BuildModule(pipelineConfig, [name: 'test-module', ecrRepo: 'test-repo'])

        // Access stages to trigger initialization
        def buildStage = buildModule.getBuild()
        def validateStage = buildModule.getValidate()
        def publishStage = buildModule.getPublish()

        assertNotNull(buildStage)
        assertNotNull(validateStage)
        assertNotNull(publishStage)

        assertTrue(buildStage instanceof BuildEnvironmentStage)
        assertTrue(validateStage instanceof ValidateEnvironmentStage)
        assertTrue(publishStage instanceof PublishDockerStage)
    }

    @Test
    void testBuildStageConfiguration() {
        buildModule = new BuildModule(pipelineConfig, [name: 'test-module'])

        def buildStage = buildModule.getBuild()
        assertNotNull(buildStage)

        // Verify BuildEnvironmentStage is properly configured
        assertTrue(buildStage instanceof BuildEnvironmentStage)
    }

    @Test
    void testValidateStageConfiguration() {
        buildModule = new BuildModule(pipelineConfig, [name: 'test-module'])

        def validateStage = buildModule.getValidate()
        assertNotNull(validateStage)

        // Verify ValidateEnvironmentStage is properly configured
        assertTrue(validateStage instanceof ValidateEnvironmentStage)
    }

    @Test
    void testPublishStageConfiguration() {
        buildModule = new BuildModule(pipelineConfig, [name: 'test-module', ecrRepo: 'test-ecr-repo'])

        def publishStage = buildModule.getPublish()
        assertNotNull(publishStage)

        // Verify PublishDockerStage is properly configured
        assertTrue(publishStage instanceof PublishDockerStage)
    }

    @Test
    void testInheritanceFromModule() {
        buildModule = new BuildModule(pipelineConfig, [name: 'test-module'])

        // Verify it properly inherits from Module
        assertNotNull(buildModule.props)
        assertNotNull(buildModule.getProperty('props'))
    }

    @Test
    void testBuildEnvironmentDefaults() {
        buildModule = new BuildModule(pipelineConfig, [:])

        def buildEnvironment = buildModule.props.buildEnvironment

        // Test all default values
        assertEquals('gradle', buildEnvironment.tool)
        assertNotNull(buildEnvironment.actions)
        assertNotNull(buildEnvironment.actions.build)
        assertEquals('dockerBuildImage -x check', buildEnvironment.actions.build.task)
    }

    @Test
    void testCustomEcrRepo() {
        def customEcrRepo = 'my-custom-ecr-repository'
        buildModule = new BuildModule(pipelineConfig, [ecrRepo: customEcrRepo])

        assertEquals(customEcrRepo, buildModule.props.ecrRepo)
    }

    @Test
    void testEmptyPropsHandling() {
        // Test with empty map
        buildModule = new BuildModule(pipelineConfig, [:])
        assertNotNull(buildModule.props)
        assertNotNull(buildModule.props.buildEnvironment)
    }

    // Mock Jenkins implementation
    class MockJenkins {
        def env = [:]
        def params = [:]
        def currentBuild = [result: null]
        List<String> echoMessages = []

        void echo(String message) {
            echoMessages.add(message)
        }

        void error(String message) {
            echoMessages.add("ERROR: " + message)
            throw new IllegalStateException(message)
        }
    }
}
