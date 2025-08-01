package pipelines.metadata

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import pipelines.module.ModuleConfig

class HelmChartUtilTest extends DeclarativePipelineTest {

    def jenkins
    def config

    private static class MockModuleConfig extends ModuleConfig {
        MockModuleConfig(Map args = [:]) {
            super(
            args.jenkins ?: new Object(),
            args.projectName ?: 'test-project',
            args.projectVersion ?: '1.0.0',
            args.vertical ?: 'test',
            args.namespace ?: 'default',
            args.metadataBucket ?: 'test-bucket',
            args.isBranchDeploy ?: false,
            args.isAutomatedDeploy ?: false,
            args.previousVersion ?: null,
            args.skipDeploymentGates ?: false,
            args.env ?: 'dev',
            args.region ?: 'us-east-1',
            args.uninstall ?: false,
            null
            )
        }
    }

    @BeforeEach
    void setUp() {
        super.setUp()
        jenkins = [
            ACCOUNTS: [
                'dev': '123456789',
                'staging': '234567890',
                'prd': '345678901',
            ],
            REGIONS: [
                'us-east-1': 'use1',
                'us-west-2': 'usw2',
                'eu-west-1': 'euw1',
            ],
            sh: { Map args ->
                if (args.script?.contains('if [ -d') && args.script?.contains('Chart.yaml')) {
                    return 'yes'  // Default: directory exists and Chart.yaml is present
                }
                return ''
            },
            readYaml: { Map args ->
                return [name: 'test-chart']  // Default: valid Chart.yaml with name
            },
            echo: { String msg ->
            },
        ]

        config = new MockModuleConfig([
            jenkins: jenkins,
            env: 'dev',
            region: 'us-east-1',
        ])
    }

    // ========== validateChartDirectory Tests ==========

    @Test
    void testValidateChartDirectorySuccess() {
        File chartDir = new File('/test/chart')

        jenkins.sh = { Map args ->
            if (args.script?.contains('if [ -d') && args.script?.contains('Chart.yaml')) {
                return 'yes'
            }
            return ''
        }

        // Should not throw exception for valid directory
        HelmChartUtil.validateChartDirectory(config, chartDir)
    }

    @Test
    void testValidateChartDirectoryInvalidDirectory() {
        File chartDir = new File('/invalid/chart')

        jenkins.sh = { Map args ->
            if (args.script?.contains('if [ -d') && args.script?.contains('Chart.yaml')) {
                return 'no'
            }
            return ''
        }

        Exception exception = assertThrows(Exception.class) {
            HelmChartUtil.validateChartDirectory(config, chartDir)
        }

        assertTrue(exception.getMessage().contains("chartDirectory='/invalid/chart' is invalid or missing Chart.yaml!"))
    }

    @Test
    void testValidateChartDirectoryMissingChartYaml() {
        File chartDir = new File('/test/chart-no-yaml')

        jenkins.sh = { Map args ->
            if (args.script?.contains('if [ -d') && args.script?.contains('Chart.yaml')) {
                return 'no'  // Directory exists but Chart.yaml is missing
            }
            return ''
        }

        Exception exception = assertThrows(Exception.class) {
            HelmChartUtil.validateChartDirectory(config, chartDir)
        }

        assertTrue(exception.getMessage().contains("is invalid or missing Chart.yaml!"))
    }

    @Test
    void testValidateChartDirectoryShellCommandStructure() {
        File chartDir = new File('/test/chart')
        def capturedCommands = []

        jenkins.sh = { Map args ->
            capturedCommands << args.script
            if (args.script?.contains('if [ -d') && args.script?.contains('Chart.yaml')) {
                return 'yes'
            }
            return ''
        }

        HelmChartUtil.validateChartDirectory(config, chartDir)

        // Verify the shell command structure
        def command = capturedCommands[0]
        assertTrue(command.contains("if [ -d '/test/chart' ]"))
        assertTrue(command.contains("[ -f '/test/chart/Chart.yaml' ]"))
        assertTrue(command.contains("echo 'yes'"))
        assertTrue(command.contains("echo 'no'"))
    }

    // ========== getChartName Tests ==========

    @Test
    void testGetChartNameSuccess() {
        File chartDir = new File('/test/chart')

        jenkins.readYaml = { Map args ->
            assertEquals('/test/chart/Chart.yaml', args.file)
            return [name: 'my-awesome-chart']
        }

        String chartName = HelmChartUtil.getChartName(config, chartDir)
        assertEquals('my-awesome-chart', chartName)
    }

    @Test
    void testGetChartNameMissingNameField() {
        File chartDir = new File('/test/chart')

        jenkins.readYaml = { Map args ->
            return [version: '1.0.0']  // Chart.yaml without name field
        }

        Exception exception = assertThrows(Exception.class) {
            HelmChartUtil.getChartName(config, chartDir)
        }

        assertTrue(exception.getMessage().contains("does not contain a 'name' field"))
        assertTrue(exception.getMessage().contains("/test/chart/Chart.yaml"))
    }

    @Test
    void testGetChartNameEmptyName() {
        File chartDir = new File('/test/chart')

        jenkins.readYaml = { Map args ->
            return [name: '']  // Empty name field
        }

        Exception exception = assertThrows(Exception.class) {
            HelmChartUtil.getChartName(config, chartDir)
        }

        assertTrue(exception.getMessage().contains("does not contain a 'name' field"))
    }

    @Test
    void testGetChartNameNullName() {
        File chartDir = new File('/test/chart')

        jenkins.readYaml = { Map args ->
            return [name: null]  // Null name field
        }

        Exception exception = assertThrows(Exception.class) {
            HelmChartUtil.getChartName(config, chartDir)
        }

        assertTrue(exception.getMessage().contains("does not contain a 'name' field"))
    }

    @Test
    void testGetChartNameWithComplexYaml() {
        File chartDir = new File('/test/complex-chart')

        jenkins.readYaml = { Map args ->
            return [
                name: 'complex-chart',
                version: '2.1.0',
                description: 'A complex helm chart',
                dependencies: [
                    [name: 'postgresql', version: '10.0.0'],
                ],
            ]
        }

        String chartName = HelmChartUtil.getChartName(config, chartDir)
        assertEquals('complex-chart', chartName)
    }

    // ========== resolveAccountId Tests ==========

    @Test
    void testResolveAccountIdDefaultBehavior() {
        config.env = 'dev'

        String accountId = HelmChartUtil.resolveAccountId(config, null)
        assertEquals('123456789', accountId)
    }

    @Test
    void testResolveAccountIdWithNullProps() {
        config.env = 'staging'

        String accountId = HelmChartUtil.resolveAccountId(config, null)
        assertEquals('234567890', accountId)
    }

    @Test
    void testResolveAccountIdWithEmptyProps() {
        config.env = 'prd'
        def props = [:]

        String accountId = HelmChartUtil.resolveAccountId(config, props)
        assertEquals('345678901', accountId)
    }

    @Test
    void testResolveAccountIdWithPropsButNoAwsAccounts() {
        config.env = 'dev'
        def props = [
            cluster: [nameSpace: 'custom-ns'],
            deploymentTimeout: '10m',
        ]

        String accountId = HelmChartUtil.resolveAccountId(config, props)
        assertEquals('123456789', accountId)
    }

    @Test
    void testResolveAccountIdWithCustomAwsAccounts() {
        config.env = 'dev'
        def props = [
            awsAccounts: [
                'dev': '999888777',
                'staging': '888777666'
            ]
        ]

        def echoMessages = []
        jenkins.echo = { String msg -> echoMessages << msg }

        String accountId = HelmChartUtil.resolveAccountId(config, props)

        assertEquals('999888777', accountId)
        assertTrue(echoMessages.any { it.contains("Using custom account for environment 'dev': 999888777") })
    }

    @Test
    void testResolveAccountIdWithCustomAwsAccountsMultipleEnvs() {
        def props = [
            awsAccounts: [
                'dev': '111222333',
                'staging': '444555666',
                'prd': '777888999'
            ]
        ]

        // Test dev environment
        config.env = 'dev'
        assertEquals('111222333', HelmChartUtil.resolveAccountId(config, props))

        // Test staging environment
        config.env = 'staging'
        assertEquals('444555666', HelmChartUtil.resolveAccountId(config, props))

        // Test prd environment
        config.env = 'prd'
        assertEquals('777888999', HelmChartUtil.resolveAccountId(config, props))
    }

    @Test
    void testResolveAccountIdEnvironmentNotFoundInCustomAccounts() {
        config.env = 'qa'  // Environment not in custom accounts
        def props = [
            awsAccounts: [
                'dev': '111222333',
                'staging': '444555666'
            ]
        ]

        assertEquals(null, HelmChartUtil.resolveAccountId(config, props))
    }

    @Test
    void testResolveAccountIdEmptyCustomAwsAccounts() {
        config.env = 'dev'
        def props = [
            awsAccounts: [:]  // Empty awsAccounts map (falsy in Groovy)
        ]

        // Empty map is falsy, so it should use default account
        String result = HelmChartUtil.resolveAccountId(config, props)

        assertEquals('123456789', result)
    }

    @Test
    void testResolveAccountNullConfigEnvironment() {
        config.env = null
        def props = [
            awsAccounts: [
                'prod': '999888777',
                'staging': '111222333'
            ]  // awsAccounts exists but doesn't contain 'dev'
        ]

        assertEquals(null, HelmChartUtil.resolveAccountId(config, props))
    }

    // ========== buildClusterName Tests ==========

    @Test
    void testBuildClusterNameDefaultFormat() {
        config.env = 'dev'
        config.region = 'us-east-1'

        String clusterName = HelmChartUtil.buildClusterName(config, null)
        assertEquals('dev-use1-apt-shared', clusterName)
    }

    @Test
    void testBuildClusterNameDefaultFormatDifferentEnvAndRegion() {
        config.env = 'staging'
        config.region = 'us-west-2'

        String clusterName = HelmChartUtil.buildClusterName(config, null)
        assertEquals('staging-usw2-apt-shared', clusterName)
    }

    @Test
    void testBuildClusterNameDefaultFormatProductionEuWest() {
        config.env = 'prd'
        config.region = 'eu-west-1'

        String clusterName = HelmChartUtil.buildClusterName(config, null)
        assertEquals('prd-euw1-apt-shared', clusterName)
    }

    @Test
    void testBuildClusterNameWithEmptyProps() {
        config.env = 'dev'
        config.region = 'us-east-1'
        def props = [:]

        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertEquals('dev-use1-apt-shared', clusterName)
    }

    @Test
    void testBuildClusterNameWithPropsButNoClusterConfig() {
        config.env = 'dev'
        config.region = 'us-east-1'
        def props = [
            awsAccounts: ['dev': '123456789'],
            deploymentTimeout: '10m',
        ]

        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertEquals('dev-use1-apt-shared', clusterName)
    }

    @Test
    void testBuildClusterNameWithPartialClusterConfig() {
        config.env = 'dev'
        config.region = 'us-east-1'
        def props = [
            cluster: [
                market: 'us',
                clusterType: 'eks'
                // Missing clusterSuffix
            ]
        ]

        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertEquals('dev-use1-apt-shared', clusterName)  // Falls back to default
    }

    @Test
    void testBuildClusterNameWithCompleteCustomClusterConfig() {
        config.env = 'dev'
        config.region = 'us-east-1'
        def props = [
            cluster: [
                market: 'us',
                clusterType: 'eks',
                clusterSuffix: 'shared'
            ]
        ]

        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertEquals('us-dev-use1-eks-shared', clusterName)
    }

    @Test
    void testBuildClusterNameWithCustomClusterConfigDifferentValues() {
        config.env = 'prd'
        config.region = 'eu-west-1'
        def props = [
            cluster: [
                market: 'eu',
                clusterType: 'gke',
                clusterSuffix: 'production'
            ]
        ]

        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertEquals('eu-prd-euw1-gke-production', clusterName)
    }

    @Test
    void testBuildClusterNameWithCustomClusterConfigMultipleScenarios() {
        def props = [
            cluster: [
                market: 'global',
                clusterType: 'k8s',
                clusterSuffix: 'cluster'
            ]
        ]

        // Test different environment and region combinations
        config.env = 'staging'
        config.region = 'us-west-2'
        assertEquals('global-staging-usw2-k8s-cluster', HelmChartUtil.buildClusterName(config, props))

        config.env = 'dev'
        config.region = 'us-east-1'
        assertEquals('global-dev-use1-k8s-cluster', HelmChartUtil.buildClusterName(config, props))
    }

    @Test
    void testBuildClusterNameWithEmptyCustomClusterValues() {
        config.env = 'dev'
        config.region = 'us-east-1'
        def props = [
            cluster: [
                market: '',
                clusterType: '',
                clusterSuffix: ''
            ]
        ]

        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertEquals('dev-use1-apt-shared', clusterName)  // Falls back to default due to empty values being falsy
    }

    @Test
    void testBuildClusterNameWithNullCustomClusterValues() {
        config.env = 'dev'
        config.region = 'us-east-1'
        def props = [
            cluster: [
                market: null,
                clusterType: null,
                clusterSuffix: null
            ]
        ]

        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertEquals('dev-use1-apt-shared', clusterName)  // Falls back to default due to null values
    }

    // ========== Integration Tests ==========

    @Test
    void testAllMethodsWithSameConfig() {
        // Test that all methods work correctly with the same config object
        File chartDir = new File('/integration/test')
        def props = [
            awsAccounts: ['dev': '999888777'],
            cluster: [
                market: 'us',
                clusterType: 'eks',
                clusterSuffix: 'shared',
            ],
        ]

        jenkins.readYaml = { Map args ->
            return [name: 'integration-chart']
        }

        // Test validateChartDirectory
        HelmChartUtil.validateChartDirectory(config, chartDir)

        // Test getChartName
        String chartName = HelmChartUtil.getChartName(config, chartDir)
        assertEquals('integration-chart', chartName)

        // Test resolveAccountId
        String accountId = HelmChartUtil.resolveAccountId(config, props)
        assertEquals('999888777', accountId)

        // Test buildClusterName
        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertEquals('us-dev-use1-eks-shared', clusterName)
    }

    @Test
    void testErrorHandlingAcrossAllMethods() {
        File invalidChartDir = new File('/invalid/chart')
        def props = [
            awsAccounts: ['staging': '123456789']  // Missing 'dev' environment
        ]

        jenkins.sh = { Map args ->
            return 'no'  // Invalid directory
        }

        jenkins.readYaml = { Map args ->
            return [version: '1.0.0']  // Missing name field
        }

        // Test that all methods properly throw exceptions
        assertThrows(Exception.class) {
            HelmChartUtil.validateChartDirectory(config, invalidChartDir)
        }

        assertThrows(Exception.class) {
            HelmChartUtil.getChartName(config, invalidChartDir)
        }

        // buildClusterName should not throw exception even with invalid props
        String clusterName = HelmChartUtil.buildClusterName(config, props)
        assertNotNull(clusterName)
    }
}
