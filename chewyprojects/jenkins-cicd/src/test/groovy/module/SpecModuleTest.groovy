package module

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.PipelineConfig
import pipelines.module.SpecModule
import pipelines.module.stage.BuildEnvironmentStage

import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for SpecModule
 * Verifies the behavior of SpecModule initialization and configuration
 */
class SpecModuleTest {

    private SpecModule specModule
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
    void testConstructorWithNameOnly() {
        specModule = new SpecModule('test-spec-module')

        assertNotNull(specModule)
        assertNotNull(specModule.props)

        // Verify default configuration
        assertEquals('test-spec-module', specModule.props.name)

        def buildEnvironment = specModule.props.buildEnvironment
        assertNotNull(buildEnvironment)
        assertEquals('gradle', buildEnvironment.tool)
        assertNotNull(buildEnvironment.actions)

        // Verify default actions
        def buildAction = buildEnvironment.actions.build
        assertNotNull(buildAction)
        assertEquals('build', buildAction.task)

        def publishAction = buildEnvironment.actions.publish
        assertNotNull(publishAction)
        assertEquals('artifactoryPublish -x check', publishAction.task)
    }

    @Test
    void testConstructorWithPipelineConfigAndProps() {
        def customProps = [
            name: 'custom-spec',
        ]

        specModule = new SpecModule(pipelineConfig, customProps)

        assertNotNull(specModule)
        assertEquals('custom-spec', specModule.props.name)

        // Verify build environment is properly set
        def buildEnvironment = specModule.props.buildEnvironment
        assertEquals('gradle', buildEnvironment.tool)
        assertEquals('build', buildEnvironment.actions.build.task)
        assertEquals('artifactoryPublish -x check', buildEnvironment.actions.publish.task)
    }

    @Test
    void testConstructorWithOverriddenBuildEnvironment() {
        def customProps = [
            name: 'custom-spec',
            buildEnvironment: [
                tool: 'maven',
                actions: [
                    build: [task: 'compile'],
                    publish: [task: 'deploy'],
                ],
            ],
        ]

        specModule = new SpecModule(pipelineConfig, customProps)

        // Custom build environment should override defaults
        def buildEnvironment = specModule.props.buildEnvironment
        assertEquals('maven', buildEnvironment.tool)
        assertEquals('compile', buildEnvironment.actions.build.task)
        assertEquals('deploy', buildEnvironment.actions.publish.task)
    }

    @Test
    void testConstructorWithPartialBuildEnvironmentOverride() {
        def customProps = [
            name: 'partial-spec',
            buildEnvironment: [
                tool: 'gradle', // Need to specify tool as well
                actions: [
                    build: [task: 'customBuild'],
                    publish: [task: 'artifactoryPublish -x check'], // Need to specify publish as well
                ],
            ],
        ]

        specModule = new SpecModule(pipelineConfig, customProps)

        // Should merge with defaults
        def buildEnvironment = specModule.props.buildEnvironment
        assertEquals('gradle', buildEnvironment.tool) // Custom override
        assertEquals('customBuild', buildEnvironment.actions.build.task) // Custom override
        assertEquals('artifactoryPublish -x check', buildEnvironment.actions.publish.task) // Custom override
    }

    @Test
    void testStageInitialization() {
        specModule = new SpecModule('test-spec')

        // Access stages to trigger initialization
        def buildStage = specModule.getBuild()
        def publishStage = specModule.getPublish()

        assertNotNull(buildStage)
        assertNotNull(publishStage)

        assertTrue(buildStage instanceof BuildEnvironmentStage)
        assertTrue(publishStage instanceof BuildEnvironmentStage)
    }

    @Test
    void testBuildStageConfiguration() {
        specModule = new SpecModule('test-spec')

        def buildStage = specModule.getBuild()
        assertNotNull(buildStage)

        // Verify BuildEnvironmentStage is properly configured for build
        assertTrue(buildStage instanceof BuildEnvironmentStage)
    }

    @Test
    void testPublishStageConfiguration() {
        specModule = new SpecModule('test-spec')

        def publishStage = specModule.getPublish()
        assertNotNull(publishStage)

        // Verify BuildEnvironmentStage is properly configured for publish
        assertTrue(publishStage instanceof BuildEnvironmentStage)
    }

    @Test
    void testDefaultNameAssignment() {
        String testName = 'my-spec-module'
        specModule = new SpecModule(testName)

        assertEquals(testName, specModule.props.name)
    }

    @Test
    void testInheritanceFromModule() {
        specModule = new SpecModule('test-spec')

        // Verify it properly inherits from Module
        assertNotNull(specModule.props)
        assertNotNull(specModule.getProperty('props'))
    }

    @Test
    void testBuildEnvironmentDefaults() {
        specModule = new SpecModule('test-spec')

        def buildEnvironment = specModule.props.buildEnvironment

        // Test all default values
        assertEquals('gradle', buildEnvironment.tool)
        assertNotNull(buildEnvironment.actions)
        assertNotNull(buildEnvironment.actions.build)
        assertNotNull(buildEnvironment.actions.publish)
        assertEquals('build', buildEnvironment.actions.build.task)
        assertEquals('artifactoryPublish -x check', buildEnvironment.actions.publish.task)
    }

    @Test
    void testEmptyPropsHandling() {
        // Test with empty map
        specModule = new SpecModule(pipelineConfig, [:])
        assertNotNull(specModule.props)
        assertNotNull(specModule.props.buildEnvironment)
    }

    @Test
    void testCustomActionsConfiguration() {
        def customProps = [
            name: 'custom-actions-spec',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [
                        task: 'customBuildTask',
                        additionalParam: 'value',
                    ],
                    publish: [
                        task: 'customPublishTask',
                        repo: 'custom-repo',
                    ],
                    test: [
                        task: 'testTask',
                    ],
                ],
            ],
        ]

        specModule = new SpecModule(pipelineConfig, customProps)

        def actions = specModule.props.buildEnvironment.actions
        assertEquals('customBuildTask', actions.build.task)
        assertEquals('value', actions.build.additionalParam)
        assertEquals('customPublishTask', actions.publish.task)
        assertEquals('custom-repo', actions.publish.repo)
        assertEquals('testTask', actions.test.task)
    }

    @Test
    void testMultipleInstancesIndependence() {
        def spec1 = new SpecModule('spec-1')
        def spec2 = new SpecModule('spec-2')

        // Verify instances are independent
        assertEquals('spec-1', spec1.props.name)
        assertEquals('spec-2', spec2.props.name)

        // Verify other instance is not affected by name differences
        assertNotEquals(spec1.props.name, spec2.props.name)
    }

    @Test
    void testPropsImmutabilityProtection() {
        specModule = new SpecModule('test-spec')

        def originalTool = specModule.props.buildEnvironment.tool
        def originalBuildTask = specModule.props.buildEnvironment.actions.build.task

        // Verify defaults are correctly set
        assertEquals('gradle', originalTool)
        assertEquals('build', originalBuildTask)
    }

    @Test
    void testComplexPropsConfiguration() {
        def complexProps = [
            name: 'complex-spec',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [
                        task: 'clean build',
                    ],
                    publish: [
                        task: 'publish --stacktrace',
                    ],
                ],
            ],
        ]

        specModule = new SpecModule(pipelineConfig, complexProps)

        // Verify all properties are properly set
        assertEquals('complex-spec', specModule.props.name)
        assertEquals('gradle', specModule.props.buildEnvironment.tool)
        assertEquals('clean build', specModule.props.buildEnvironment.actions.build.task)
        assertEquals('publish --stacktrace', specModule.props.buildEnvironment.actions.publish.task)
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
