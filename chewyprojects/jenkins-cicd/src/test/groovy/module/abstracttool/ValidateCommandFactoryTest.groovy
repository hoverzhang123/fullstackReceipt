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
 * Test class for ValidateCommandFactory
 * Verifies the behavior of validate command factory
 */
class ValidateCommandFactoryTest {

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

        // Initialize properties map with required gradle validate configuration
        propsMap = [
            name: 'testModule',
            buildEnvironment: [
                tool: 'gradle',
            ],
        ]
        moduleProps = new ModuleProps(propsMap)

        // Create command context with validate action
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('validate')
                .build()
    }

    @Test
    void testCreateGradleValidateCommand() {
        propsMap.buildEnvironment.tool = 'gradle'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('validate')
                .build()

        def command = ValidateCommandFactory.createValidateCommand(moduleConfig, commandContext)

        assertNotNull(command)
        assertTrue(command instanceof GradleValidateCommand)
        assertTrue(jenkins.echoMessages.any { it.contains('Validate tool strategy: gradle') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using standard Gradle validation strategy') })
    }

    @Test
    void testCreateGradleDockerValidateCommand() {
        propsMap.buildEnvironment.tool = 'gradledocker'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('validate')
                .build()

        def command = ValidateCommandFactory.createValidateCommand(moduleConfig, commandContext)

        assertNotNull(command)
        assertTrue(command instanceof DockerxValidateCommand)
        assertTrue(jenkins.echoMessages.any { it.contains('Validate tool strategy: gradledocker') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using Gradle Docker validation strategy') })
    }

    @Test
    void testCreatePythonValidateCommand() {
        propsMap.buildEnvironment.tool = 'python'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('validate')
                .build()

        def command = ValidateCommandFactory.createValidateCommand(moduleConfig, commandContext)

        assertNotNull(command)
        assertTrue(command instanceof GradleValidateCommand)
        assertTrue(jenkins.echoMessages.any { it.contains('Validate tool strategy: python') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using Python validation strategy (gradle-based)') })
    }

    @Test
    void testCreateDefaultValidateCommand() {
        propsMap.buildEnvironment.tool = 'unknown'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('validate')
                .build()

        def command = ValidateCommandFactory.createValidateCommand(moduleConfig, commandContext)

        assertNotNull(command)
        assertTrue(command instanceof GradleValidateCommand)
        assertTrue(jenkins.echoMessages.any { it.contains('Validate tool strategy: unknown') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using default validation strategy for tool: unknown -> GradleValidateCommand') })
    }

    @Test
    void testCreateValidateCommandWithNullBuildEnvironment() {
        propsMap.buildEnvironment = null
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('validate')
                .build()

        assertThrows(IllegalArgumentException.class) {
            ValidateCommandFactory.createValidateCommand(moduleConfig, commandContext)
        }

        assertTrue(jenkins.echoMessages.any { it.contains('Error: buildEnvironment is required but not provided for project: test-project') })
    }

    @Test
    void testCreateValidateCommandWithNullTool() {
        propsMap.buildEnvironment = [:]
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('validate')
                .build()

        assertThrows(IllegalArgumentException.class) {
            ValidateCommandFactory.createValidateCommand(moduleConfig, commandContext)
        }

        assertTrue(jenkins.echoMessages.any { it.contains('ERROR: buildEnvironment.tool is required but not provided for project: test-project') })
    }
}
