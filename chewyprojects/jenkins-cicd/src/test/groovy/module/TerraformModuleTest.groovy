package module

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.PipelineConfig
import pipelines.module.Module
import pipelines.module.ModuleConfig
import pipelines.module.TerraformModule
import pipelines.module.stage.DockerBuildStage
import static org.junit.jupiter.api.Assertions.*

class TerraformModuleTest {
    def mockJenkins
    PipelineConfig mockPipelineConfig
    TerraformModule module

    @BeforeEach
    void setUp() {
        mockJenkins = [
            env: ['ENVIRONMENT': 'dev'],
            ACCOUNTS: ['shd': '123456789012'],
        ]
        mockPipelineConfig = PipelineConfig.builder()
                .jenkins(mockJenkins)
                .build()

        module = new TerraformModule(mockPipelineConfig, [name: 'my-terraform', ecrRepo: '/my/repo'])
    }

    @Test
    void testModuleName() {
        assertEquals('my-terraform', module.name)
    }

    @Test
    void testInitializeStages() {
        // Calling a getter will trigger initializeStages()
        def buildStage = module.getBuild()
        assertTrue(buildStage instanceof DockerBuildStage)

        def validateStage = module.getValidate()
        assertFalse(validateStage instanceof Module.EmptyStage)

        def publishStage = module.getPublish()
        assertFalse(publishStage instanceof Module.EmptyStage)

        def globalDeployStage = module.getGlobalDeploy()
        assertFalse(globalDeployStage instanceof Module.EmptyStage)

        // Test that other stages are EmptyStage
        assertTrue(module.getPreCheck() instanceof Module.EmptyStage)
        assertTrue(module.getRegionDeploy() instanceof Module.EmptyStage)
        assertTrue(module.getPostCheck() instanceof Module.EmptyStage)
        assertTrue(module.getRegionPostDeploy() instanceof Module.EmptyStage)
        assertTrue(module.getGlobalRollback() instanceof Module.EmptyStage)
        assertTrue(module.getRegionRollback() instanceof Module.EmptyStage)
    }

    @Test
    void testPublishTerraformWithPreviousVersion() {
        def shellCommands = []
        mockJenkins.ecrLogin = { -> 'ecr-login-command' }
        mockJenkins.sh = { cmd ->
            shellCommands.add(cmd.strip().toString())
        }

        def mockConfig = ModuleConfig.builder()
                .jenkins(mockJenkins)
                .projectName('test-project')
                .projectVersion('1.2.3')
                .vertical('test-vertical')
                .namespace('test-namespace')
                .metadataBucket('test-bucket')
                .previousVersion('1.2.2')
                .build()

        module.publishTerraform(mockConfig)

        assertEquals(3, shellCommands.size())
        assertEquals('ecr-login-command', shellCommands[0])
        assertTrue(shellCommands[1].contains('docker tag 123456789012.dkr.ecr.us-east-1.amazonaws.com/my/repo:terraform-1.2.2 123456789012.dkr.ecr.us-east-1.amazonaws.com/my/repo:terraform-1.2.3'))
        assertEquals('docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/my/repo:terraform-1.2.3', shellCommands[2])
    }

    @Test
    void testPublishTerraformWithoutPreviousVersion() {
        def shellCommands = []
        mockJenkins.ecrLogin = { -> 'ecr-login-command' }
        mockJenkins.sh = { cmd -> shellCommands.add(cmd.strip().toString()) }

        def mockConfig = ModuleConfig.builder()
                .jenkins(mockJenkins)
                .projectName('test-project')
                .projectVersion('1.2.3')
                .vertical('test-vertical')
                .namespace('test-namespace')
                .metadataBucket('test-bucket')
                .previousVersion(null)
                .build()

        module.publishTerraform(mockConfig)

        assertEquals(2, shellCommands.size())
        assertEquals('ecr-login-command', shellCommands[0])
        assertEquals('docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/my/repo:terraform-1.2.3', shellCommands[1])
    }
}
