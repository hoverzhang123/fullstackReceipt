package module.abstracttool

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.module.CommandContext
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps
import pipelines.module.abstracttool.DockerxValidateCommand
import module.TestUtil

import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for DockerxValidateCommand
 * Verifies the behavior of Docker validate command execution
 */
class DockerxValidateCommandTest {

    DockerxValidateCommand dockerxValidateCommand
    CommandContext commandContext
    ModuleProps moduleProps
    Map propsMap
    ModuleConfig moduleConfig
    def jenkins

    @BeforeEach
    void setup() {
        // Initialize properties map with required docker validate configuration
        propsMap = [
            name: 'testModule',
            buildEnvironment: [
                tool: 'gradledocker',
            ],
        ]
        moduleProps = new ModuleProps(propsMap)

        // Create command context with validate action
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('validate')
                .build()

        // Initialize the command under test
        dockerxValidateCommand = new DockerxValidateCommand(commandContext)

        // Create mock Jenkins environment
        jenkins = TestUtil.createMockJenkins()

        // Create ModuleConfig with required properties
        moduleConfig = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectVersion("1.0.0")
                .build()
    }

    @Test
    void testExecuteThrowsUnsupportedOperationException() {
        // Verify that execute throws UnsupportedOperationException as it's not fully implemented
        assertThrows(UnsupportedOperationException.class) {
            dockerxValidateCommand.execute(moduleConfig, 'testModule')
        }

        // Verify debug message was logged before exception
        assertTrue(jenkins.echoMessages.any { it.contains('DockerxValidateCommand executing with buildEnvironment:') })
    }

    @Test
    void testGetDescription() {
        String description = dockerxValidateCommand.getDescription()

        assertNotNull(description)
        assertEquals("Docker Validate with container-based validation", description)
    }

    @Test
    void testConstructor() {
        assertNotNull(dockerxValidateCommand)
        assertEquals(commandContext, dockerxValidateCommand.commandContext)
    }
}
