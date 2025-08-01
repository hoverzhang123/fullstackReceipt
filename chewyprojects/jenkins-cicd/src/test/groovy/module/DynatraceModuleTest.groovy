package module

import com.lesfurets.jenkins.unit.RegressionTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import pipelines.module.ModuleConfig
import pipelines.module.DynatraceMonitorModule
import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest

import static org.junit.jupiter.api.Assertions.*


class DynatraceModuleTest extends DeclarativePipelineTest implements RegressionTest {

    DynatraceMonitorModule dynatraceMonitorModule
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
            return ''
        })
        helper.registerAllowedMethod('sh', [Map.class], null)
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
        helper.registerAllowedMethod('sleep', [Integer.class], null)
        // Register readJSON using the helper - this is for global pipeline steps
        helper.registerAllowedMethod('readJSON', [Map.class], { Map args ->
            if (args.text?.contains('access_token')) {
                return [access_token: "mock-token", token_type: "Bearer"]
            }
            if (args.text?.contains('results')) {
                return [results: [[id: "workflow-123"]]]
            }
            if (args.text?.contains('execution-123') && !args.text.contains('tasks')) {
                return [id: "execution-123", state: "SUCCESS"]
            }
            if (args.text?.contains('"validation_status": "success"')) {
                return [
                    task1: [result: [validation_status: "success"]],
                    task2: [result: ["batch.status": "success"]],
                ]
            }
            if (args.text?.contains('"validation_status": "fail"')) {
                return [
                    task1: [result: [validation_status: "fail"]],
                    task2: [result: ["batch.status": "fail"]],
                ]
            }
            return [:]
        })

        mockJenkins = TestUtil.createMockJenkins()

        // Store original sh implementation from TestUtil
        def originalSh = mockJenkins.sh

        // Also add readJSON directly to the mockJenkins object for jenkins.readJSON() calls
        mockJenkins.readJSON = { Map args ->
            if (args.text?.contains('access_token')) {
                return [access_token: "mock-token", token_type: "Bearer", ]
            }
            if (args.text?.contains('results')) {
                return [results: [[id: "workflow-123"]], ]
            }
            if (args.text?.contains('execution-123') && !args.text.contains('tasks')) {
                return [id: "execution-123", state: "SUCCESS", ]
            }
            if (args.text?.contains('"validation_status": "success"')) {
                return [
                    task1: [result: [validation_status: "success"]],
                    task2: [result: ["batch.status": "success"]],
                ]
            }
            if (args.text?.contains('"validation_status": "fail"')) {
                return [
                    task1: [result: [validation_status: "fail"]],
                    task2: [result: ["batch.status": "fail"]],
                ]
            }
            return [:]
        }

        // Override sh method in mockJenkins
        mockJenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                if (args.script.contains('curl') && args.script.contains('sso.dynatrace.com/sso/oauth2/token')) {
                    return '{"access_token": "mock-token", "token_type": "Bearer"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/workflows') && !args.script.contains('/run')) {
                    return '{"results": [{"id": "workflow-123"}]}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/workflows/workflow-123/run')) {
                    return '{"id": "execution-123"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/executions/execution-123') && !args.script.contains('/tasks')) {
                    return '{"state": "SUCCESS"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/executions/execution-123/tasks')) {
                    return '{"task1": {"result": {"validation_status": "success"}}, "task2": {"result": {"batch.status": "success"}}}'
                }
            }
            // Fallback to original sh implementation for other cases
            return originalSh(args)
        }

        // Create a mock for the currentBuild global variable
        binding.setVariable('currentBuild', [:])

        mockJenkins.notifyFailure = { projectName, message ->
        }

        // Override the error method to avoid exceptions for our test
        mockJenkins.error = { message ->
            // If we're checking execution status, don't throw an exception
            if (message.contains("Dynatrace workflow execution failed or timeout")) {
                println "Mock: ${message}"
            }
            else {
                throw new Exception(message)
            }
        }

        mockJenkins.currentBuild = [ result: '' ]

        // Add getSecret method directly to mockJenkins
        mockJenkins.getSecret = { String path ->
            if (path == "dynatrace-terraform/cicdsrgexec/nonprod-us-app-cicd-oath") {
                return "mock-dynatrace-oauth-secret"
            }
            return "mock-secret"
        }

        // Override secretsmanager method to return specific values
        mockJenkins.secretsmanager = [
            getKVSecret: { Object... args ->
                return '{"client_id": "mock-client-id", "client_secret": "mock-dynatrace-oauth-secret"}'
            },
        ]

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

        // Create DynatraceMonitorModule with test configuration using the builder pattern
        dynatraceMonitorModule = DynatraceMonitorModule.builder()
                .postCheckAttemptsNonPrd()
                .postCheckIntervalSecondsNonPrd()
                .postCheckAttemptsPrd()
                .postCheckIntervalSecondsPrd()
                .prdFailureStatus()
                .nonPrdFailureStatus()
                .autoRollbackOnFailure()
                .build()
    }

    @Test
    void testShouldCheckMonitors() {
        boolean result = dynatraceMonitorModule.shouldCheckMonitors(mockConfig)
        assertTrue(result)
    }

    @Test
    void testShouldDeployObservability() {
        def echoCalled = false
        def echoMessage = ''

        mockJenkins.echo = { String msg ->
            echoCalled = true
            echoMessage = msg
        }

        boolean result = dynatraceMonitorModule.shouldDeployObservability(mockConfig)

        assertTrue(echoCalled)
        assertTrue(echoMessage.contains("test here: false"))
        assertFalse(result)
    }

    @Test
    void testCheckMonitorsSuccess() {
        boolean result = dynatraceMonitorModule.checkMonitors(mockConfig, true)
        assertTrue(result)
    }

    @Test
    void testCheckMonitorsError() {
        // Override sh method in mockJenkins to return failed tasks
        mockJenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                if (args.script.contains('curl') && args.script.contains('sso.dynatrace.com/sso/oauth2/token')) {
                    return '{"access_token": "mock-token", "token_type": "Bearer"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/workflows') && !args.script.contains('/run')) {
                    return '{"results": [{"id": "workflow-123"}]}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/workflows/workflow-123/run')) {
                    return '{"id": "execution-123"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/executions/execution-123') && !args.script.contains('/tasks')) {
                    return '{"state": "SUCCESS"}'
                }
                // this is the only condition that needed a modified response
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/executions/execution-123/tasks')) {
                    return '{"task1": {"result": {"validation_status": "fail"}}, "task2": {"result": {"batch.status": "fail"}}}'
                }
            }
            // Fallback to original sh implementation for other cases
            mockJenkins = TestUtil.createMockJenkins()

            // Store original sh implementation from TestUtil
            def originalSh = mockJenkins.sh
            return originalSh(args)
        }

        // Override the error method to avoid exceptions for our test
        mockJenkins.error = { message ->
            println(message)
        }

        boolean result = dynatraceMonitorModule.checkMonitors(mockConfig, true)
        assertFalse(result)
    }

    @Test
    void testCheckMonitorsWarn() {
        // Override sh method in mockJenkins to return failed tasks
        mockJenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                if (args.script.contains('curl') && args.script.contains('sso.dynatrace.com/sso/oauth2/token')) {
                    return '{"access_token": "mock-token", "token_type": "Bearer"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/workflows') && !args.script.contains('/run')) {
                    return '{"results": [{"id": "workflow-123"}]}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/workflows/workflow-123/run')) {
                    return '{"id": "execution-123"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/executions/execution-123') && !args.script.contains('/tasks')) {
                    return '{"state": "SUCCESS"}'
                }
                // this is the only condition that needed a modified response
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/executions/execution-123/tasks')) {
                    return '{"task1": {"result": {"validation_status": "fail"}}, "task2": {"result": {"batch.status": "fail"}}}'
                }
            }
            // Fallback to original sh implementation for other cases
            mockJenkins = TestUtil.createMockJenkins()

            // Store original sh implementation from TestUtil
            def originalSh = mockJenkins.sh
            return originalSh(args)
        }

        // Override the error method to avoid exceptions for our test
        mockJenkins.error = { message ->
            // If we're checking execution status, don't throw an exception
            if (message.contains("Dynatrace workflow execution failed or timeout")) {
                println "Mock: ${message}"
            }
            else {
                throw new Exception(message)
            }
        }

        // Create DynatraceMonitorModule with test configuration using the builder pattern
        def failureDynatraceMonitorModule = DynatraceMonitorModule.builder()
                .postCheckAttemptsNonPrd()
                .postCheckIntervalSecondsNonPrd()
                .postCheckAttemptsPrd()
                .postCheckIntervalSecondsPrd()
                .prdFailureStatus()
                .nonPrdFailureStatus('WARN')
                .autoRollbackOnFailure()
                .build()

        boolean result = failureDynatraceMonitorModule.checkMonitors(mockConfig, true)
        assertFalse(result)
    }

    @Test
    void testShouldCheckMonitorsWhenNotSkippingGates() {
        // Ensure shouldCheckMonitors returns true when skipDeploymentGates is false
        assertTrue(dynatraceMonitorModule.shouldCheckMonitors(mockConfig),
                "Should check monitors when skipDeploymentGates is false")
    }

    @Test
    void testShouldNotCheckMonitorsWhenSkippingGates() {
        // Create a config with skipDeploymentGates set to true
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

        assertFalse(dynatraceMonitorModule.shouldCheckMonitors(configWithSkipGates),
                "Should not check monitors when skipDeploymentGates is true")
    }

    @Test
    void testWorkflowSuccessPass() {
        boolean result = dynatraceMonitorModule.workflowSuccess(mockConfig, 0)
        assertTrue(result)
    }

    @Test
    void testWorkflowSuccessFail() {
        // Override sh method in mockJenkins to return failed tasks
        mockJenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                if (args.script.contains('curl') && args.script.contains('sso.dynatrace.com/sso/oauth2/token')) {
                    return '{"access_token": "mock-token", "token_type": "Bearer"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/workflows') && !args.script.contains('/run')) {
                    return '{"results": [{"id": "workflow-123"}]}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/workflows/workflow-123/run')) {
                    return '{"id": "execution-123"}'
                }
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/executions/execution-123') && !args.script.contains('/tasks')) {
                    return '{"state": "SUCCESS"}'
                }
                // this is the only condition that needed a modified response
                if (args.script.contains('curl') && args.script.contains('/platform/automation/v1/executions/execution-123/tasks')) {
                    return '{"task1": {"result": {"validation_status": "fail"}}, "task2": {"result": {"batch.status": "fail"}}}'
                }
            }
            // Fallback to original sh implementation for other cases
            mockJenkins = TestUtil.createMockJenkins()

            // Store original sh implementation from TestUtil
            def originalSh = mockJenkins.sh
            return originalSh(args)
        }

        def echoCalled = false
        def echoMessage = ''

        mockJenkins.echo = { String msg ->
            echoCalled = true
            echoMessage = msg
        }

        boolean result = dynatraceMonitorModule.workflowSuccess(mockConfig, 0)
        assertTrue(echoCalled)
        assertTrue(echoMessage.contains("Dynatrace workflow validation failed, check https://dlg34900.apps.dynatrace.com/ui/apps/dynatrace.automations/executions/execution-123"))
        assertFalse(result)
    }

    @Test
    void testGetBearerTokenError() {
        // Override sh method in mockJenkins to return failed tasks
        mockJenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                if (args.script.contains('curl') && args.script.contains('sso.dynatrace.com/sso/oauth2/token')) {
                    return ''
                }
            }
            // Fallback to original sh implementation for other cases
            mockJenkins = TestUtil.createMockJenkins()

            // Store original sh implementation from TestUtil
            def originalSh = mockJenkins.sh
            return originalSh(args)
        }

        mockJenkins.error = { message ->
            if (message.contains("Dynatrace workflow execution failed or timeout")) {
                println "Mock: ${message}"
                throw new Exception(message)
            }
        }

        assertThrows(Exception.class, {
            dynatraceMonitorModule.workflowSuccess(mockConfig, 0)
        })
    }

    @Test
    void testGetSecretsError() {
        mockJenkins.error = { message ->
            println(message)
        }
        def originalSecretsManager = mockJenkins.secretsmanager
        // Override secretsmanager method to throw exception
        mockJenkins.secretsmanager = [
            getKVSecret: {
                throw new Exception()
            }
        ]
        // Create a ModuleConfig using the builder pattern
        def prodConfig = ModuleConfig.builder()
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
        assertThrows(Exception.class, {
            dynatraceMonitorModule.workflowSuccess(prodConfig, 0)
        })
        mockJenkins.secretsmanager = originalSecretsManager
    }

    @Test
    void siteReliabilityGuardianFails() {
        // Create a failing tasks response that will be correctly detected by siteReliabilityGuardianSuccess
        def failingTasks = [
            task1: [result: [validation_status: "FAIL"]],
            task2: [result: [validation_status: "ERROR"]],
        ]

        // Initialize the module with the mock
        def module = new pipelines.module.DynatraceMonitorModule(5, 30, 3, 10, "ERROR", "WARN", true, true)
        def originalMethods = [:]

        // Store original method(s)
        originalMethods.siteReliabilityGuardianSuccess = module.metaClass.getMetaMethod("siteReliabilityGuardianSuccess", Object, Object)

        // Test the method directly
        boolean result = module.siteReliabilityGuardianFailed(failingTasks)
        assertTrue(result)
    }

    @Test
    void syntheticWorkflowFail() {
        def failingTasks = [
            task1: [result: ["batch.status": "success"]],
            task2: [result: ["batch.status": "FAIL"]],
            task3: [result: ["batch.status": "ERROR"]],
        ]

        def module = new pipelines.module.DynatraceMonitorModule(5, 30, 3, 10, "ERROR", "WARN", true, true)
        def originalMethods = [:]
        originalMethods.syntheticWorkflowSuccess = module.metaClass.getMetaMethod("syntheticWorkflowSuccess", Object)

        boolean result = module.syntheticWorkflowFailed(failingTasks)
        assertTrue(result)
    }
}
