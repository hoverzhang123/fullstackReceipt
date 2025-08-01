package module.abstracttool

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.module.CommandContext
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps
import pipelines.module.abstracttool.*
import module.TestUtil

import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for BuildCommandFactory
 * Verifies the behavior of build command factory
 */
class BuildCommandFactoryTest {

    CommandContext commandContext
    ModuleProps moduleProps
    Map propsMap
    ModuleConfig moduleConfig
    def jenkins

    @BeforeEach
    void setup() {
        // Create mock Jenkins environment
        jenkins = TestUtil.createMockJenkins()

        // Create ModuleConfig with required properties
        moduleConfig = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectName("test-project")
                .projectVersion("1.0.0")
                .build()

        // Initialize properties map with required gradle build configuration
        propsMap = [
            name: 'testModule',
            buildEnvironment: [
                tool: 'gradle',
            ],
        ]
        moduleProps = new ModuleProps(propsMap)

        // Create command context with build action
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()
    }

    @Test
    void testCreateGradleBuildCommand() {
        propsMap.buildEnvironment.tool = 'gradle'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        def command = BuildCommandFactory.createBuildCommand(moduleConfig, commandContext)

        assertNotNull(command)
        assertTrue(command instanceof GradleBuildCommand)
        assertTrue(jenkins.echoMessages.any { it.contains('Build tool strategy: gradle') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using standard Gradle build strategy') })
    }

    @Test
    void testCreateGradleDockerBuildCommand() {
        propsMap.buildEnvironment.tool = 'gradledocker'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        def command = BuildCommandFactory.createBuildCommand(moduleConfig, commandContext)

        assertNotNull(command)
        assertTrue(command instanceof DockerxBuildCommand)
        assertTrue(jenkins.echoMessages.any { it.contains('Build tool strategy: gradledocker') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using Gradle Docker build strategy') })
    }

    @Test
    void testCreatePythonBuildCommand() {
        propsMap.buildEnvironment.tool = 'python'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        def command = BuildCommandFactory.createBuildCommand(moduleConfig, commandContext)

        assertNotNull(command)
        assertTrue(command instanceof GradleBuildCommand)
        assertTrue(jenkins.echoMessages.any { it.contains('Build tool strategy: python') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using Python build strategy') })
    }

    @Test
    void testCreateDefaultBuildCommand() {
        propsMap.buildEnvironment.tool = 'unknown'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        def command = BuildCommandFactory.createBuildCommand(moduleConfig, commandContext)

        assertNotNull(command)
        assertTrue(command instanceof GradleBuildCommand)
        assertTrue(jenkins.echoMessages.any { it.contains('Build tool strategy: unknown') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using default build strategy for tool: GradleBuildCommand') })
    }

    @Test
    void testCreateBuildCommandWithNullBuildEnvironment() {
        propsMap.buildEnvironment = null
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        assertThrows(IllegalArgumentException.class) {
            BuildCommandFactory.createBuildCommand(moduleConfig, commandContext)
        }

        assertTrue(jenkins.echoMessages.any { it.contains('Error: buildEnvironment is required but not provided for project: test-project') })
    }

    @Test
    void testCreateBuildCommandWithNullTool() {
        propsMap.buildEnvironment = [:]
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        assertThrows(IllegalArgumentException.class) {
            BuildCommandFactory.createBuildCommand(moduleConfig, commandContext)
        }

        assertTrue(jenkins.echoMessages.any { it.contains('ERROR: buildEnvironment.tool is required but not provided for project: test-project') })
    }
}
