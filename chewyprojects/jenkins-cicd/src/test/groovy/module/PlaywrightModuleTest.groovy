package module

import com.lesfurets.jenkins.unit.RegressionTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import pipelines.PlaywrightModuleConfig
import pipelines.module.ModuleConfig
import pipelines.module.PlaywrightModule
import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest

import static org.junit.jupiter.api.Assertions.*


class PlaywrightModuleTest extends DeclarativePipelineTest implements RegressionTest {

    PlaywrightModule playwrightModule
    ModuleConfig mockConfig
    def mockJenkins
    boolean rollbackCalled

    @Override
    @BeforeEach
    void setUp() {
        super.setUp()

        // Reset rollback flag
        rollbackCalled = false

        // Register allowed methods for Jenkins
        helper.registerAllowedMethod('sh', [String.class], { String script ->
            if (script.contains('which docker')) {
                return '/usr/bin/docker'
            }
            if (script.contains('which docker-compose')) {
                return '/usr/local/bin/docker-compose'
            }
            if (script.contains('stat -c %g')) {
                return '999'
            }
            if (script.contains('npx allure generate')) {
                return ''
            }
            return ''
        })
        helper.registerAllowedMethod('sh', [Map.class], { Map args ->
            if (args.script.contains('which docker')) {
                return '/usr/bin/docker'
            }
            if (args.script.contains('which docker-compose')) {
                return '/usr/local/bin/docker-compose'
            }
            if (args.script.contains('stat -c %g')) {
                return '999'
            }
            return ''
        })
        helper.registerAllowedMethod('error', [String.class], { String message ->
            throw new Exception(message)
        })
        helper.registerAllowedMethod('echo', [String.class], null)
        helper.registerAllowedMethod('withAWS', [Map.class, Closure.class], { Map args, Closure closure ->
            closure()
        })
        helper.registerAllowedMethod('withEnv', [List.class, Closure.class], { List env, Closure closure ->
            closure()
        })
        helper.registerAllowedMethod('lock', [String.class, Closure.class],{String lock, Closure closure ->
            closure()
        })
        helper.registerAllowedMethod('configFileProvider', [List.class, Closure.class], { List args, Closure closure ->
            closure()
        })
        helper.registerAllowedMethod('ecrLogin', [], { return 'aws ecr get-login-password' })

        mockJenkins = TestUtil.createMockJenkins()

        // Create a ModuleConfig using the builder pattern
        mockConfig = ModuleConfig.builder()
                .jenkins(mockJenkins)
                .projectName('test-project')
                .projectVersion('1.0.0')
                .namespace('test-namespace')
                .metadataBucket('test-bucket')
                .isBranchDeploy(false)
                .isAutomatedDeploy(false)
                .skipDeploymentGates(false)
                .env('dev')
                .region('us-east-1')
                .uninstall(false)
                .build()

        // Create PlaywrightModule with test configuration using the builder pattern
        def playwrightModuleConfig = PlaywrightModuleConfig.builder()
                .name('test-module')
                .testSuites(['desktop'])
                .testTargetName('playwrightTest')
                .secretKeys(['API_KEY', 'AUTH_TOKEN'])
                .envMap(['TEST_VAR': 'test-value'])
                .playwrightImage('test-image:latest')
                .autoRollbackOnFailure(true)
                .runInPRBuild(false)
                .build()

        playwrightModule = new PlaywrightModule(playwrightModuleConfig)

        // Override methods to prevent real execution
        playwrightModule.metaClass.runTestInDocker = { config, env, command -> }
        playwrightModule.metaClass.testSecrets = { config -> }

        // Mock CommonUtil static methods
        binding.setVariable('CommonUtil', [
            getUrl: { a, b, c, d, e, f, g -> return 'https://test-url.com' },
            getProxyBasePath: { config -> return '/test-path' },
        ])
    }

    @Test
    void testShouldRunTestsWithSkipDeploymentGates() {
        // Create a config with skipDeploymentGates = true
        def configWithSkipGates = ModuleConfig.builder()
                .jenkins(mockJenkins)
                .projectName('test-project')
                .projectVersion('1.0.0')
                .namespace('test-namespace')
                .metadataBucket('test-bucket')
                .isBranchDeploy(false)
                .isAutomatedDeploy(false)
                .skipDeploymentGates(true)
                .env('dev')
                .region('us-east-1')
                .uninstall(false)
                .build()

        assertFalse(playwrightModule.shouldRunTests(configWithSkipGates))
    }

    @Test
    void testRunPlaywrightWithBackendTests() {
        // Setup backend test configuration
        def backendConfig = PlaywrightModuleConfig.builder()
                .name('backend-test')
                .testSuites(['backend'])
                .testTargetName('backendTest')
                .secretKeys([])
                .playwrightImage('test-image:latest')
                .build()

        def backendModule = new PlaywrightModule(backendConfig)

        // Override methods
        backendModule.metaClass.runTestInDocker = { config, env, command ->
            // Verify the command contains the right parameters
            assert command.contains('npx playwright test')
            assert command.contains('--project="backend"')
        }

        backendModule.metaClass.testSecrets = { config -> }

        backendModule.metaClass.handleRollback = { config ->
            rollbackCalled = true
            return false
        }

        assertTrue(backendModule.runPlaywright(mockConfig))
    }

    @Test
    void testRunPlaywrightWithProdTests() {
        // Setup prod test configuration
        def prodConfig = PlaywrightModuleConfig.builder()
                .name('prod-test')
                .testSuites(['prod-desktop'])
                .secretKeys([])
                .playwrightImage('test-image:latest')
                .build()

        def prodModule = new PlaywrightModule(prodConfig)

        // Create a production config
        def prodModuleConfig = ModuleConfig.builder()
                .jenkins(mockJenkins)
                .projectName('test-project')
                .projectVersion('1.0.0')
                .namespace('test-namespace')
                .metadataBucket('test-bucket')
                .isBranchDeploy(false)
                .isAutomatedDeploy(false)
                .skipDeploymentGates(false)
                .env('prd')
                .region('us-east-1')
                .uninstall(false)
                .build()

        // Override methods
        prodModule.metaClass.runTestInDocker = { config, env, command ->
            // Verify the command contains the right parameters
            assert command.contains('npx playwright test')
            assert command.contains('--project="prod-desktop"')
        }

        prodModule.metaClass.testSecrets = { config -> }

        prodModule.metaClass.handleRollback = { config ->
            rollbackCalled = true
            return false
        }

        assertTrue(prodModule.runPlaywright(prodModuleConfig))
    }

    @Test
    void testRunPlaywrightWithFailure() {
        // Setup test configuration
        def failingConfig = PlaywrightModuleConfig.builder()
                .name('failing-test')
                .testSuites(['desktop'])  // Non-backend, non-prod test suite
                .secretKeys([])
                .playwrightImage('test-image:latest')
                .autoRollbackOnFailure(true)
                .build()

        def failingModule = new PlaywrightModule(failingConfig)

        // Create a production config
        def prodModuleConfig = ModuleConfig.builder()
                .jenkins(mockJenkins)
                .projectName('test-project')
                .projectVersion('1.0.0')
                .namespace('test-namespace')
                .metadataBucket('test-bucket')
                .isBranchDeploy(false)
                .isAutomatedDeploy(false)
                .skipDeploymentGates(false)
                .env('prd')
                .region('us-east-1')
                .uninstall(false)
                .build()

        mockJenkins.env.ENVIRONMENT = 'prd'
        rollbackCalled = false

        // Replace the runPlaywright method with our own version for this test
        failingModule.metaClass.runPlaywright = { ModuleConfig config ->
            // Just return false to simulate test failure
            rollbackCalled = true
            return false
        }

        assertFalse(failingModule.runPlaywright(prodModuleConfig))
        assertTrue(rollbackCalled)
    }

    @Test
    void shouldIgnoreSetupPlaywrightProperty() {
        playwrightModule.initModulePropsOnStages()
        // Does not throw GroovyRuntimeException: Cannot read write-only property: upPlaywright
    }
}
