package module

import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import pipelines.module.stage.HelmRollbackStage
import pipelines.module.ModuleConfig
import pipelines.metadata.HelmChartUtil
import pipelines.metadata.CommonUtil
import pipelines.module.ModuleProps

class HelmRollbackStageTest extends DeclarativePipelineTest {

    private static final String TEST_ACCOUNT_ID = '123456789012'
    private static final String WORKSPACE_PATH = '/tmp'
    private static final String TEST_REGION = 'us-east-1'
    private static final String TEST_NAMESPACE = 'ns'
    private static final String TEST_PROJECT_NAME = 'svc'
    private static final String TEST_ROLLBACK_VERSION = '1.2.3-abc'
    private static final Map<String, String> TEST_ACCOUNTS = [
        dev: TEST_ACCOUNT_ID,
        shd: TEST_ACCOUNT_ID,
        prd: TEST_ACCOUNT_ID,
    ]
    private static final Map<String, String> TEST_REGIONS = [
        (TEST_REGION): 'use1'
    ]

    def helmRollbackStage
    def jenkins
    def config

    @BeforeEach
    void setUp() {
        super.setUp()
        helmRollbackStage = new HelmRollbackStage()
        jenkins = new MockJenkins()
        jenkins.ACCOUNTS = TEST_ACCOUNTS
        jenkins.REGIONS = TEST_REGIONS
        jenkins.env = [WORKSPACE: WORKSPACE_PATH]
        jenkins.k8s = [generateNamespaceName: { -> 'test-branch-ns' }]

        config = new MockModuleConfig(
                jenkins,
                TEST_PROJECT_NAME,
                "1.0.0",
                "vertical",
                TEST_NAMESPACE,
                "bucket",
                false,
                false,
                TEST_ROLLBACK_VERSION,
                false,
                "dev",
                TEST_REGION,
                false,
                null
                )
    }

    private static class MockModuleConfig extends ModuleConfig {
        MockModuleConfig(Object jenkins, String projectName, String projectVersion, String vertical, String namespace, String metadataBucket, boolean isBranchDeploy, boolean isAutomatedDeploy, String previousVersion, boolean skipDeploymentGates, String env, String region, boolean uninstall, Object extra) {
            super(jenkins, projectName, projectVersion, vertical, namespace, metadataBucket, isBranchDeploy, isAutomatedDeploy, previousVersion, skipDeploymentGates, env, region, uninstall, extra)
        }
        String rollbackVersion
    }

    @Test
    void testInitializeStageSetsRunner() {
        helmRollbackStage.initializeStage()
        assertNotNull(helmRollbackStage.runner)
    }

    @Test
    void testHelmRollbackWithVersion() {
        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                return "5"
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        assertTrue(shCalls.any { it.toString().contains('helm history') }, 'Should call helm history')
        assertTrue(shCalls.any { it.toString().contains('yacli helm rollback') }, 'Should call yacli helm rollback')
        assertTrue(shCalls.any { it.toString().contains('echo login') }, 'Should call ecrLogin')
        assertTrue(shCalls.any { it.toString().contains('aws eks') && it.toString().contains('update-kubeconfig') }, 'Should update kubeconfig')
    }

    @Test
    void testHelmRollbackWithoutVersion() {
        config.previousVersion = null
        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                return ""
            }
            return null
        }

        // Mock CommonUtil.getRollbackVersion to return null
        def originalMetaClass = CommonUtil.metaClass
        CommonUtil.metaClass.static.getRollbackVersion = { j, p, n, e -> null }

        try {
            helmRollbackStage.helmRollback(config)
            // Current implementation still calls yacli helm rollback even when no version is available
            // The rollback command is executed with empty revision number
            assertTrue(shCalls.any { it.toString().contains('yacli helm rollback') }, 'Should call yacli helm rollback even when no version available')
        } finally {
            CommonUtil.metaClass = originalMetaClass
        }
    }

    @Test
    void testHelmRollbackPrdNotification() {
        config.env = "prd"
        def notified = false
        def notificationArgs = []
        jenkins.notify = { a, b, c ->
            notified = true
            notificationArgs = [a, b, c]
        }
        jenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                return '3'
            } else {
                return null
            }
        }

        helmRollbackStage.helmRollback(config)

        assertTrue(notified, "Should send notification for production environment")
        assertEquals(TEST_PROJECT_NAME, notificationArgs[0])
        assertTrue(notificationArgs[1].contains("Rollback succeeded"))
        assertTrue(notificationArgs[1].contains(TEST_ROLLBACK_VERSION))
        assertEquals("blue", notificationArgs[2])
    }

    @Test
    void testHelmRollbackNonPrdEcho() {
        config.env = "dev"
        def echoMessages = []
        jenkins.echo = { msg -> echoMessages << msg }
        jenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                return '3'
            } else {
                return null
            }
        }

        helmRollbackStage.helmRollback(config)

        def hasSuccessMessage = echoMessages.any {
            it.contains("Rollback succeeded") && it.contains(TEST_ROLLBACK_VERSION)
        }
        assertTrue(hasSuccessMessage, "Should echo success message for non-production environment")
    }

    @Test
    void testHelmRollbackWithPropsNamespace() {
        def propsMap = [cluster: [nameSpace: 'props-namespace']]
        def props = new ModuleProps(propsMap)
        helmRollbackStage.setModuleProps(props)

        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                return "4"
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        def rollbackCommand = shCalls.find { it.toString().contains('yacli helm rollback') }
        // Current implementation uses config.namespace, not props.cluster.nameSpace
        assertTrue(rollbackCommand.toString().contains('--force-namespace ' + TEST_NAMESPACE),
                "Should use config namespace")
    }

    @Test
    void testHelmRollbackBranchDeploy() {
        config.isBranchDeploy = true
        def echoMessages = []
        jenkins.echo = { msg -> echoMessages << msg }

        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                return "2"
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        // Should generate branch namespace
        def hasBranchNamespaceMessage = echoMessages.any {
            it.contains('HelmRollbackStage: generated branch namespace = test-branch-ns')
        }
        assertTrue(hasBranchNamespaceMessage, "Should echo generated branch namespace message")

        // Should use generated namespace in rollback command
        def rollbackCommand = shCalls.find { it.toString().contains('yacli helm rollback') }
        assertTrue(rollbackCommand.toString().contains('--force-namespace test-branch-ns'),
                "Should use generated branch namespace")
    }

    @Test
    void testHelmRollbackNamespaceEcho() {
        def echoMessages = []
        jenkins.echo = { msg -> echoMessages << msg }
        jenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                return "1"
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        def hasNamespaceMessage = echoMessages.any {
            it.contains('HelmRollbackStage: namespace is') &&
                    it.contains(TEST_NAMESPACE) &&
                    it.contains('environment dev') &&
                    it.contains('region us-east-1')
        }
        assertTrue(hasNamespaceMessage, "Should echo namespace information")
    }

    @Test
    void testHelmRollbackAwsAccountAndCluster() {
        // Mock HelmChartUtil static methods
        def originalMetaClass = HelmChartUtil.metaClass
        HelmChartUtil.metaClass.static.resolveAccountId = { ModuleConfig c, def p -> '987654321' }
        HelmChartUtil.metaClass.static.buildClusterName = { ModuleConfig c, def p -> 'test-cluster' }

        try {
            def shCalls = []
            jenkins.sh = { args ->
                shCalls << args
                if (args instanceof Map && args.returnStdout) {
                    return "6"
                }
                return null
            }

            helmRollbackStage.helmRollback(config)

            // Check kubeconfig command uses correct cluster
            def kubeconfigCommand = shCalls.find {
                it.toString().contains('aws eks') && it.toString().contains('update-kubeconfig')
            }
            assertTrue(kubeconfigCommand.toString().contains('--name test-cluster'),
                    "Should use correct cluster name")

            // Check rollback command uses correct cluster
            def rollbackCommand = shCalls.find { it.toString().contains('yacli helm rollback') }
            assertTrue(rollbackCommand.toString().contains('--cluster-name test-cluster'),
                    "Should use correct cluster name in rollback")
        } finally {
            HelmChartUtil.metaClass = originalMetaClass
        }
    }

    @Test
    void testHelmRollbackRevisionNumberExtraction() {
        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                return "7"  // Mock revision number
            }
            return null
        }

        def echoMessages = []
        jenkins.echo = { msg -> echoMessages << msg }

        helmRollbackStage.helmRollback(config)

        // Should echo the revision number
        def hasRevisionMessage = echoMessages.any {
            it.contains('rollback revision number = 7')
        }
        assertTrue(hasRevisionMessage, "Should echo rollback revision number")

        // Should use revision number in rollback command
        def rollbackCommand = shCalls.find { it.toString().contains('yacli helm rollback') }
        assertTrue(rollbackCommand.toString().contains('7'),
                "Should include revision number in rollback command")
    }

    @Test
    void testHelmRollbackWithEmptyRevisionNumber() {
        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                return ""  // Empty revision number
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        // Should still call rollback but without revision number
        def rollbackCommand = shCalls.find { it.toString().contains('yacli helm rollback') }
        assertTrue(rollbackCommand != null, "Should still call yacli helm rollback")
        assertTrue(rollbackCommand.toString().contains('--release-name ' + TEST_PROJECT_NAME),
                "Should include release name")
    }

    @Test
    void testHelmRollbackDeployStepSetting() {
        jenkins.sh = { args ->
            if (args instanceof Map && args.returnStdout) {
                return "8"
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        assertTrue(jenkins.env.DEPLOY_STEP.toString().contains("Helm Release / Rollback - dev us-east-1"))
    }

    @Test
    void testHelmRollbackWithDifferentEnvironments() {
        ['dev', 'staging', 'prd'].each { env ->
            config.env = env
            jenkins.env.DEPLOY_STEP = null

            jenkins.sh = { args ->
                if (args instanceof Map && args.returnStdout) {
                    return "9"
                }
                return null
            }

            helmRollbackStage.helmRollback(config)

            assertTrue(jenkins.env.DEPLOY_STEP.contains(env),
                    "DEPLOY_STEP should contain environment: ${env}")
        }
    }

    @Test
    void testHelmRollbackWithDifferentRegions() {
        [
            'us-east-1',
            'us-west-2',
            'eu-west-1',
        ].each { region ->
            config.region = region
            jenkins.env.DEPLOY_STEP = null

            jenkins.sh = { args ->
                if (args instanceof Map && args.returnStdout) {
                    return "10"
                }
                return null
            }

            helmRollbackStage.helmRollback(config)

            assertTrue(jenkins.env.DEPLOY_STEP.contains(region),
                    "DEPLOY_STEP should contain region: ${region}")
        }
    }

    @Test
    void testHelmRollbackHelmHistoryCommand() {
        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                return "11"
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        def historyCommand = shCalls.find { it.toString().contains('helm history') }
        assertTrue(historyCommand.toString().contains(TEST_PROJECT_NAME),
                "History command should include project name")
        assertTrue(historyCommand.toString().contains('-n ' + TEST_NAMESPACE),
                "History command should include namespace")
    }

    @Test
    void testHelmRollbackYacliCommand() {
        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                return "13"
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        def rollbackCommand = shCalls.find { it.toString().contains('yacli helm rollback') }
        assertTrue(rollbackCommand.toString().contains('--release-name ' + TEST_PROJECT_NAME),
                "Should include release name")
        assertTrue(rollbackCommand.toString().contains('--cluster-name'),
                "Should include cluster name")
        assertTrue(rollbackCommand.toString().contains('--force-namespace'),
                "Should include force namespace")
    }

    @Test
    void testHelmRollbackRollbackVersionParsing() {
        config.previousVersion = '3.4.5-xyz123'

        def shCalls = []
        jenkins.sh = { args ->
            shCalls << args
            if (args instanceof Map && args.returnStdout) {
                // Check if it's the grep command for finding revision
                if (args.toString().contains('grep') && args.toString().contains('3.4.5')) {
                    return "14"
                }
                return "14"
            }
            return null
        }

        helmRollbackStage.helmRollback(config)

        // Should search for the specific rollback version ID (part before the dash)
        def grepCommand = shCalls.find {
            it.toString().contains('grep 3.4.5')
        }
        assertTrue(grepCommand != null, "Should grep for specific rollback version")
    }

    // MockJenkins for pipeline simulation
    private static class MockJenkins {

        Map ACCOUNTS = [:]
        Map REGIONS = [:]
        Map env = [:]
        Map k8s = [:]
        def sh = { args -> }
        def ecrLogin = { -> 'echo login' }
        def withAWS = { Map map, Closure c -> c() }
        def docker = [image: { String img -> [inside: { String opts, Closure c -> c() }] }]
        def echo = { msg -> }
        def notify = { a, b, c -> }

    }

}
