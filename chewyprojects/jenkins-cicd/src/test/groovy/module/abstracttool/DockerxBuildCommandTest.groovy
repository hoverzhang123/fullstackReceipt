package module.abstracttool

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.module.CommandContext
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps
import pipelines.module.abstracttool.DockerxBuildCommand
import module.TestUtil

import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for DockerxBuildCommand
 * Verifies the behavior of Docker buildx command execution
 */
class DockerxBuildCommandTest {

    DockerxBuildCommand dockerxBuildCommand
    CommandContext commandContext
    ModuleProps moduleProps
    Map propsMap
    ModuleConfig moduleConfig
    def jenkins

    @BeforeEach
    void setup() {
        // Initialize properties map with required docker build configuration
        propsMap = [
            name: 'testModule',
            buildEnvironment: [
                tool: 'gradledocker',
                actions: [
                    build: [dockerfilepath: 'Dockerfile'],
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
        dockerxBuildCommand = new DockerxBuildCommand(commandContext)

        // Create mock Jenkins environment
        jenkins = TestUtil.createMockJenkins()

        // Create ModuleConfig with required properties
        moduleConfig = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectVersion("1.0.0")
                .build()
    }

    @Test
    void testExecuteWithValidDockerfilePath() {
        // Execute docker build command
        dockerxBuildCommand.execute(moduleConfig, 'testModule')

        // Verify debug messages
        assertTrue(jenkins.echoMessages.any { it.contains('DockerxBuildCommand executing with buildEnvironment:') })
        assertTrue(jenkins.echoMessages.any { it.contains('Using dockerfile at: Dockerfile') })
    }

    @Test
    void testExecuteWithCustomDockerfilePath() {
        // Configure custom dockerfile path
        propsMap.buildEnvironment.actions.build.dockerfilepath = 'docker/custom.Dockerfile'
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()
        dockerxBuildCommand = new DockerxBuildCommand(commandContext)

        dockerxBuildCommand.execute(moduleConfig, 'testModule')

        assertTrue(jenkins.echoMessages.any { it.contains('Using dockerfile at: docker/custom.Dockerfile') })
    }

    @Test
    void testExecuteWithMissingDockerfilePath() {
        // Remove dockerfile path
        propsMap.buildEnvironment.actions.build.remove('dockerfilepath')
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()
        dockerxBuildCommand = new DockerxBuildCommand(commandContext)

        assertThrows(IllegalArgumentException.class) {
            dockerxBuildCommand.execute(moduleConfig, 'testModule')
        }
    }

    @Test
    void testExecuteWithMissingBuildAction() {
        // Remove build action
        propsMap.buildEnvironment.actions = [:]
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('build')
                .build()
        dockerxBuildCommand = new DockerxBuildCommand(commandContext)

        assertThrows(IllegalArgumentException.class) {
            dockerxBuildCommand.execute(moduleConfig, 'testModule')
        }
    }

    @Test
    void testExecuteWithDifferentAction() {
        // Add publish action
        propsMap.buildEnvironment.actions.publish = [dockerfilepath: 'publish.Dockerfile']
        moduleProps = new ModuleProps(propsMap)
        commandContext = CommandContext.builder()
                .props(moduleProps)
                .action('publish')
                .build()
        dockerxBuildCommand = new DockerxBuildCommand(commandContext)

        dockerxBuildCommand.execute(moduleConfig, 'testModule')

        assertTrue(jenkins.echoMessages.any { it.contains('Using dockerfile at: publish.Dockerfile') })
    }

    @Test
    void testGetDescription() {
        String description = dockerxBuildCommand.getDescription()

        assertNotNull(description)
        assertEquals("Docker buildx build (supports multi-architecture and targets)", description)
    }
}
