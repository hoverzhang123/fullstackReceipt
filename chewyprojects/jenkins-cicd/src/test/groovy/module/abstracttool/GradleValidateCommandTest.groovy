package module.abstracttool

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.module.CommandContext
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps
import pipelines.module.abstracttool.GradleValidateCommand
import module.TestUtil

import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for GradleValidateCommand
 * Verifies the behavior of Gradle validate command execution
 */
class GradleValidateCommandTest {

    GradleValidateCommand gradleValidateCommand
    CommandContext commandContext
    ModuleProps moduleProps
    Map propsMap
    ModuleConfig moduleConfig
    def jenkins

    @BeforeEach
    void setup() {
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

        // Initialize the command under test
        gradleValidateCommand = new GradleValidateCommand(commandContext)

        // Create mock Jenkins environment
        jenkins = TestUtil.createMockJenkins()

        // Create ModuleConfig with required properties
        moduleConfig = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectVersion("1.0.0")
                .build()
    }

    @Test
    void testExecuteWithTestResults() {
        // Mock sh command to return test results
        jenkins.sh = { args ->
            // Handle both Map and String arguments
            if (args instanceof Map) {
                if (args.returnStdout && args.script.contains('find . | grep test-results')) {
                    return './build/test-results/test/TEST-SomeTest.xml'
                }
                return ''
            } else if (args instanceof String) {
                // Handle string-based sh calls from GradleCommandUtil
                return ''
            }
            return ''
        }

        // Mock junit method
        jenkins.junit = { pattern ->
            jenkins.echoMessages.add("JUnit called with pattern: ${pattern}")
        }

        gradleValidateCommand.execute(moduleConfig, 'testModule')

        // Verify debug message
        assertTrue(jenkins.echoMessages.any { it.contains('GradleValidateCommand executing with buildEnvironment:') })

        // Verify junit was called
        assertTrue(jenkins.echoMessages.any { it.contains('JUnit called with pattern: **/test-results/**/*.xml') })
    }

    @Test
    void testExecuteWithoutTestResults() {
        // Mock sh command to return empty test results
        jenkins.sh = { args ->
            // Handle both Map and String arguments
            if (args instanceof Map) {
                if (args.returnStdout && args.script.contains('find . | grep test-results')) {
                    return ''
                }
                return ''
            } else if (args instanceof String) {
                // Handle string-based sh calls from GradleCommandUtil
                return ''
            }
            return ''
        }

        // Mock junit method
        jenkins.junit = { pattern ->
            jenkins.echoMessages.add("JUnit called with pattern: ${pattern}")
        }

        gradleValidateCommand.execute(moduleConfig, 'testModule')

        // Verify debug message
        assertTrue(jenkins.echoMessages.any { it.contains('GradleValidateCommand executing with buildEnvironment:') })

        // Verify junit was NOT called
        assertFalse(jenkins.echoMessages.any { it.contains('JUnit called with pattern:') })
    }

    @Test
    void testExecuteWithNullModule() {
        gradleValidateCommand.execute(moduleConfig, null)

        // Should still execute and log the message
        assertTrue(jenkins.echoMessages.any { it.contains('GradleValidateCommand executing with buildEnvironment:') })
    }

    @Test
    void testGetDescription() {
        String description = gradleValidateCommand.getDescription()

        assertNotNull(description)
        assertEquals("Gradle Validate with check and sonar tasks", description)
    }

    @Test
    void testConstructor() {
        assertNotNull(gradleValidateCommand)
        assertEquals(commandContext, gradleValidateCommand.commandContext)
    }
}
