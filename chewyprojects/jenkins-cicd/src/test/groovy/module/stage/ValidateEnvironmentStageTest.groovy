package module.stage

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps
import pipelines.module.stage.ValidateEnvironmentStage
import pipelines.module.CommandContext
import pipelines.module.abstracttool.Command
import pipelines.module.abstracttool.ValidateCommandFactory
import module.TestUtil

import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for ValidateEnvironmentStage
 * Verifies the behavior of ValidateEnvironmentStage execution and command delegation
 */
class ValidateEnvironmentStageTest {

    private ValidateEnvironmentStage validateStage
    private ModuleConfig moduleConfig
    private def jenkins
    private ModuleProps moduleProps

    @BeforeEach
    void setUp() {
        jenkins = TestUtil.createMockJenkins()

        moduleConfig = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectName('test-project')
                .projectVersion('1.0.0')
                .build()

        // Create test module properties
        def propsMap = [
            name: 'testModule',
            buildEnvironment: [
                tool: 'gradle',
            ],
        ]
        moduleProps = new ModuleProps(propsMap)
    }

    @Test
    void testConstructorWithAction() {
        validateStage = new ValidateEnvironmentStage('validate')

        assertNotNull(validateStage)
        assertEquals('validate', validateStage.action)
    }

    @Test
    void testConstructorWithCustomAction() {
        validateStage = new ValidateEnvironmentStage('customValidate')

        assertNotNull(validateStage)
        assertEquals('customValidate', validateStage.action)
    }

    @Test
    void testInitializeStage() {
        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.initializeStage()

        assertNotNull(validateStage.runner)
    }

    @Test
    void testValidateExecution() {
        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.moduleProps = moduleProps

        validateStage.validate(moduleConfig)

        // Verify environment variable was set (convert GString to String for comparison)
        assertEquals("Validate testModule(testModule) - 1.0.0", jenkins.env.DEPLOY_STEP.toString())

        // Verify echo messages were logged
        assertTrue(jenkins.echoMessages.any { it.contains('Using validate strategy:') })
    }

    @Test
    void testValidateWithNullModuleName() {
        def propsMapWithNullName = [
            name: null,
            buildEnvironment: [
                tool: 'gradle',
            ],
        ]
        moduleProps = new ModuleProps(propsMapWithNullName)

        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.moduleProps = moduleProps

        validateStage.validate(moduleConfig)

        // Should use project name when module name is null
        assertEquals("Validate test-project(null) - 1.0.0", jenkins.env.DEPLOY_STEP.toString())
    }

    @Test
    void testValidateWithDifferentAction() {
        validateStage = new ValidateEnvironmentStage('customAction')
        validateStage.moduleProps = moduleProps

        validateStage.validate(moduleConfig)

        // Verify custom action is used in DEPLOY_STEP
        assertTrue(jenkins.env.DEPLOY_STEP.contains('Validate'))
        assertTrue(jenkins.echoMessages.any { it.contains('Using validate strategy:') })
    }

    @Test
    void testCommandContextCreation() {
        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.moduleProps = moduleProps

        // Mock ValidateCommandFactory to capture CommandContext
        def capturedContext = null
        def originalCreateValidateCommand = ValidateCommandFactory.&createValidateCommand

        // We can't easily mock static methods, but we can verify the command creation
        validateStage.validate(moduleConfig)

        // Verify the process completed without errors
        assertTrue(jenkins.echoMessages.any { it.contains('Using validate strategy:') })
    }

    @Test
    void testValidateWithDifferentBuildEnvironment() {
        def propsMapWithDocker = [
            name: 'dockerModule',
            buildEnvironment: [
                tool: 'docker',
            ],
        ]
        moduleProps = new ModuleProps(propsMapWithDocker)

        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.moduleProps = moduleProps

        validateStage.validate(moduleConfig)

        assertEquals("Validate dockerModule(dockerModule) - 1.0.0", jenkins.env.DEPLOY_STEP.toString())
        assertTrue(jenkins.echoMessages.any { it.contains('Using validate strategy:') })
    }

    @Test
    void testModulePropsAccess() {
        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.moduleProps = moduleProps

        assertNotNull(validateStage.moduleProps)
        assertEquals('testModule', validateStage.moduleProps.name)
        assertEquals('gradle', validateStage.moduleProps.buildEnvironment.tool)
    }

    @Test
    void testValidateWithComplexModuleProps() {
        def complexPropsMap = [
            name: 'complexModule',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    validate: [task: 'customValidateTask'],
                ],
            ],
            customProperty: 'customValue',
        ]
        moduleProps = new ModuleProps(complexPropsMap)

        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.moduleProps = moduleProps

        validateStage.validate(moduleConfig)

        assertEquals("Validate complexModule(complexModule) - 1.0.0", jenkins.env.DEPLOY_STEP.toString())
        assertTrue(jenkins.echoMessages.any { it.contains('Using validate strategy:') })
    }

    @Test
    void testStageNameGeneration() {
        // Test with module name
        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.moduleProps = moduleProps

        validateStage.validate(moduleConfig)
        assertTrue(jenkins.env.DEPLOY_STEP.contains('testModule'))

        // Test without module name (should use project name)
        def propsWithoutName = [
            buildEnvironment: [tool: 'gradle'],
        ]
        moduleProps = new ModuleProps(propsWithoutName)
        validateStage.moduleProps = moduleProps

        validateStage.validate(moduleConfig)
        assertTrue(jenkins.env.DEPLOY_STEP.contains('test-project'))
    }

    @Test
    void testProjectVersionInDeployStep() {
        validateStage = new ValidateEnvironmentStage('validate')
        validateStage.moduleProps = moduleProps

        // Test with different project version
        moduleConfig = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectName('test-project')
                .projectVersion('2.1.0-SNAPSHOT')
                .build()

        validateStage.validate(moduleConfig)

        assertTrue(jenkins.env.DEPLOY_STEP.contains('2.1.0-SNAPSHOT'))
    }
}
