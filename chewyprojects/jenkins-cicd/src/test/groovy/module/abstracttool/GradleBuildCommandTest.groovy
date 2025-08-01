package module.abstracttool

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.module.CommandContext
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps
import pipelines.module.abstracttool.GradleBuildCommand
import module.TestUtil

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Test class for GradleBuildCommand
 * Verifies the behavior of Gradle build command execution
 */
class GradleBuildCommandTest {

    GradleBuildCommand gradleBuildCommand
    CommandContext commandContext
    ModuleProps moduleProps
    Map propsMap
    ModuleConfig moduleConfig
    def jenkins

    @BeforeEach
    void setup() {
        // Initialize properties map with required gradle build configuration
        propsMap = [
            name: 'testModule',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [task: 'testTask'],
                ],
            ],
        ]
        moduleProps = new ModuleProps(propsMap)

        // Create command context with build action
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        // Initialize the command under test
        gradleBuildCommand = new GradleBuildCommand(commandContext)

        // Create mock Jenkins environment using project's TestUtil
        jenkins = TestUtil.createMockJenkins()

        // Create ModuleConfig with required properties
        moduleConfig = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectVersion("1.0.0")
                .build()
    }

    /**
     * Test default build behavior with standard configuration
     */
    @Test
    void testDefaultBehavior() {
        // Execute build command with test module
        // This will use the configured actions.build.task
        gradleBuildCommand.execute(moduleConfig, 'testModule')
    }

    /**
     * Test build action with custom build task
     */
    @Test
    void testBuildAction() {
        // Configure custom build task
        propsMap.buildEnvironment.actions.build.task = 'customBuild'
        gradleBuildCommand.execute(moduleConfig, 'testModule')
    }

    /**
     * Test publish action with standard configuration
     */
    @Test
    void testPublishAction() {
        // Add publish action to configuration
        propsMap.buildEnvironment.actions.publish = [task: 'publishTask']

        // Create new command context with publish action
        commandContext = CommandContext.builder()
                .props(new ModuleProps(propsMap))
                .action('publish')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        gradleBuildCommand.execute(moduleConfig, 'testModule')
    }

    /**
     * Test fallback behavior when no action task is configured
     */
    @Test
    void testFallbackBehaviorForBuild() {
        // Remove build task to trigger fallback
        propsMap.buildEnvironment.actions = [:]
        moduleProps = new ModuleProps(propsMap)

        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        gradleBuildCommand.execute(moduleConfig, 'testModule')

        // Verify fallback echo messages
        assertTrue(jenkins.echoMessages.any { it.contains('Executing in \'actions fallback\' mode') })
        assertTrue(jenkins.echoMessages.any { it.contains('No specific action config found, falling back to default task') })
    }

    /**
     * Test fallback behavior for publish action
     */
    @Test
    void testFallbackBehaviorForPublish() {
        // Remove all actions to trigger fallback
        propsMap.buildEnvironment.actions = [:]
        moduleProps = new ModuleProps(propsMap)

        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('publish')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        gradleBuildCommand.execute(moduleConfig, 'testModule')

        // Verify fallback echo messages
        assertTrue(jenkins.echoMessages.any { it.contains('Executing in \'actions fallback\' mode') })
        assertTrue(jenkins.echoMessages.any { it.contains('falling back to default task: \'testModule:artifactoryPublish -x check\'') })
    }

    /**
     * Test with null actions map
     */
    @Test
    void testNullActionsMap() {
        propsMap.buildEnvironment.actions = null
        moduleProps = new ModuleProps(propsMap)

        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        gradleBuildCommand.execute(moduleConfig, 'testModule')

        // Should trigger fallback behavior
        assertTrue(jenkins.echoMessages.any { it.contains('Executing in \'actions fallback\' mode') })
    }

    /**
     * Test with empty build environment
     */
    @Test
    void testEmptyBuildEnvironment() {
        propsMap.buildEnvironment = [:]
        moduleProps = new ModuleProps(propsMap)

        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        gradleBuildCommand.execute(moduleConfig, 'testModule')

        // Should handle gracefully
        assertTrue(jenkins.echoMessages.any { it.contains('Executing in \'actions\' mode') })
    }

    /**
     * Test getDescription method
     */
    @Test
    void testGetDescription() {
        String description = gradleBuildCommand.getDescription()

        assertNotNull(description)
        assertTrue(description.contains('testModule'))
        assertTrue(description.contains('build'))
        assertEquals("Gradle command executor name is testModule and action: 'build.'", description)
    }

    /**
     * Test getDescription with different action
     */
    @Test
    void testGetDescriptionWithPublishAction() {
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('PUBLISH')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        String description = gradleBuildCommand.getDescription()

        assertNotNull(description)
        assertTrue(description.contains('testModule'))
        assertTrue(description.contains('publish'))
        assertEquals("Gradle command executor name is testModule and action: 'publish.'", description)
    }

    /**
     * Test case insensitive action handling
     */
    @Test
    void testCaseInsensitiveAction() {
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('BUILD')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        gradleBuildCommand.execute(moduleConfig, 'testModule')

        // Should work with uppercase action
        assertTrue(jenkins.echoMessages.any { it.contains('Executing in \'actions\' mode') })
    }

    /**
     * Test with different module name
     */
    @Test
    void testWithDifferentModuleName() {
        propsMap.name = 'differentModule'
        moduleProps = new ModuleProps(propsMap)

        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        String description = gradleBuildCommand.getDescription()

        assertTrue(description.contains('differentModule'))
    }

    /**
     * Test with custom task containing special characters
     */
    @Test
    void testCustomTaskWithSpecialCharacters() {
        propsMap.buildEnvironment.actions.build.task = 'build --parallel -x test'
        moduleProps = new ModuleProps(propsMap)

        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        gradleBuildCommand.execute(moduleConfig, 'testModule')

        // Should handle special characters in task name
        assertTrue(jenkins.echoMessages.any { it.contains('Executing task from actions: \'build --parallel -x test\'') })
    }

    /**
     * Test echo messages during execution
     */
    @Test
    void testEchoMessagesLogging() {
        gradleBuildCommand.execute(moduleConfig, 'testModule')

        // Verify specific echo messages
        assertTrue(jenkins.echoMessages.any { it.contains('Executing in \'actions\' mode') })
        assertTrue(jenkins.echoMessages.any { it.contains('actions: ') })
        assertTrue(jenkins.echoMessages.any { it.contains('task: testTask') })
        assertTrue(jenkins.echoMessages.any { it.contains('Executing task from actions: \'testTask\'') })
    }

    /**
     * Test default task generation for unknown actions
     */
    @Test
    void testDefaultTaskForUnknownAction() {
        // Remove all actions to trigger fallback with unknown action
        propsMap.buildEnvironment.actions = [:]
        moduleProps = new ModuleProps(propsMap)

        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('unknownAction')
                .build()

        gradleBuildCommand = new GradleBuildCommand(commandContext)
        gradleBuildCommand.execute(moduleConfig, 'testModule')

        // Should execute fallback but with null default task (handled gracefully)
        assertTrue(jenkins.echoMessages.any { it.contains('Executing in \'actions fallback\' mode') })
    }
}
