package module

import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import pipelines.module.stage.HelmReleaseStage
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps
import pipelines.metadata.HelmChartUtil

class HelmReleaseStageTest extends DeclarativePipelineTest {

    def helmReleaseStage
    def config
    def jenkins
    def chartModule

    private static class MockConfig extends ModuleConfig {
        MockConfig(Map args = [:]) {
            super(
            args.jenkins ?: new Object(),
            args.projectName ?: 'testproj',
            args.projectVersion ?: '1.2.3',
            args.vertical ?: '',
            args.namespace ?: 'default',
            args.metadataBucket ?: '',
            args.isBranchDeploy ?: false,
            args.isAutomatedDeploy ?: false,
            args.previousVersion ?: null,
            args.skipDeploymentGates ?: false,
            args.env ?: 'dev',
            args.region ?: 'us-east-1',
            args.uninstall ?: false,
            null
            )
            this.helmChartModule = args.helmChartModule
            this.chartTarBallFile = args.chartTarBallFile
            this.chartName = args.chartName
            this.deploymentTimeout = args.deploymentTimeout
        }
        def helmChartModule
        def chartTarBallFile
        def chartName
        def deploymentTimeout
    }

    @BeforeEach
    void setUp() {
        super.setUp()
        helmReleaseStage = new HelmReleaseStage()
        jenkins = [
            DEPLOY_STEP: '',
            ACCOUNTS: ['dev': '123', 'shd': '456', 'prd': '789'],
            REGIONS: ['us-east-1': 'use1', 'us-west-2': 'usw2', 'eu-west-1': 'euw1'],
            env: [
                ARTIFACTORY_USER: 'user',
                ARTIFACTORY_PASSWORD: 'pass',
                WORKSPACE: '/tmp',
                GROUP: 'group',
            ],
            k8s: [generateNamespaceName: { -> 'test-ns' }],
            withAWS: { args, closure ->
                try {
                    closure()
                } catch (Exception e) {
                    jenkins.echo("withAWS exception: ${e.getMessage()}")
                    throw e
                }
            },
            docker: [image: { img -> [inside: { args, closure -> closure() }] }],
            sh: { cmd ->
                jenkins.lastSh = cmd
                jenkins.allShCommands = jenkins.allShCommands ?: []
                jenkins.allShCommands.add(cmd)
            },
            echo: { msg ->
                jenkins.lastEcho = msg
                jenkins.allEchoMessages = jenkins.allEchoMessages ?: []
                jenkins.allEchoMessages.add(msg)
            },
        ]
        chartModule = [
            validateChartDirectory: { ModuleConfig c -> },
            pullChart: { ModuleConfig c -> },
            getChartTarBallFile: { ModuleConfig c -> new File('artifacts/helm/test_app-1.2.3.tgz') },
            getChartName: { ModuleConfig c -> 'test_app' },
            deploymentTimeout: '10m0s',
        ]
        config = new MockConfig([
            jenkins: jenkins,
            helmChartModule: chartModule,
            env: 'dev',
            region: 'us-east-1',
            projectVersion: '1.2.3',
            namespace: 'default',
            isBranchDeploy: false,
            projectName: 'testproj',
            chartTarBallFile: new File('/tmp/chart.tgz'),
        ])
    }

    @Test
    void testInitializeStageSetsRunner() {
        helmReleaseStage.initializeStage()
        assertNotNull(helmReleaseStage.runner)
    }

    @Test
    void testHelmReleaseWithChartModule() {
        helmReleaseStage.helmRelease(config)
        assertTrue(jenkins.DEPLOY_STEP.contains('Helm Release / Deployment'))
        assertTrue(jenkins.lastSh.contains('yacli helm deploy'))
        assertTrue(jenkins.lastSh.contains('--chart-tarball  "artifacts/helm/test_app-1.2.3.tgz"'))
        assertTrue(jenkins.lastSh.contains('--chart-name test_app'))
        assertTrue(jenkins.lastSh.contains('--helm-timeout 10m0s'))
    }

    @Test
    void testHelmReleaseWithoutChartModuleFallbackLogic() {
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/fallback.tgz')
        config.chartName = 'test_app_fallback'
        config.deploymentTimeout = '8m0s'

        helmReleaseStage.helmRelease(config)

        def hasFallbackMessage = jenkins.allEchoMessages.any {
            it.contains('Using fallback logic for helm release configuration')
        }
        assertTrue(hasFallbackMessage, "Should echo fallback logic message")

        assertTrue(jenkins.lastSh.contains('--chart-tarball  "/tmp/fallback.tgz"'))
        assertTrue(jenkins.lastSh.contains('--chart-name test_app_fallback'))
        assertTrue(jenkins.lastSh.contains('--helm-timeout 8m0s'))
    }

    @Test
    void testHelmReleaseWithoutChartModuleUsesDefaultTimeout() {
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/fallback.tgz')
        config.chartName = 'test_app_fallback'
        config.deploymentTimeout = null
        helmReleaseStage.helmRelease(config)
        assertTrue(jenkins.lastSh.contains('--helm-timeout 5m0s'))
    }

    @Test
    void testHelmReleaseWithPropsDeploymentTimeout() {
        def props = new ModuleProps([deploymentTimeout: '20m0s'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/fallback.tgz')
        config.chartName = 'test_app_fallback'
        config.deploymentTimeout = null

        helmReleaseStage.helmRelease(config)
        assertTrue(jenkins.lastSh.contains('--helm-timeout 20m0s'), "Should use props deployment timeout")
    }

    @Test
    void testHelmReleaseBranchDeployNamespace() {
        config.isBranchDeploy = true
        helmReleaseStage.helmRelease(config)

        def helmDeployCommand = jenkins.allShCommands.find { it.contains('yacli helm deploy') }
        assertTrue(helmDeployCommand != null, "Should have a helm deploy command")
        assertTrue(helmDeployCommand.contains('--force-namespace test-ns'), "Should use generated namespace test-ns")

        def hasGeneratedNamespaceMessage = jenkins.allEchoMessages.any {
            it.contains('HelmReleaseStage: generated branch namespace = test-ns')
        }
        assertTrue(hasGeneratedNamespaceMessage, "Should echo the generated namespace message")
    }

    @Test
    void testHelmReleaseWithPropsChartDirectory() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = null
        config.chartName = null

        def originalMetaClass = HelmChartUtil.metaClass
        HelmChartUtil.metaClass.static.validateChartDirectory = { ModuleConfig c, File dir -> }
        HelmChartUtil.metaClass.static.getChartName = { ModuleConfig c, File dir -> 'props-chart' }

        try {
            helmReleaseStage.helmRelease(config)

            def hasDerivingMessage = jenkins.allEchoMessages.any {
                it.contains('config.chartName is null and try to derive values from moduleProps')
            }
            assertTrue(hasDerivingMessage, "Should echo message about deriving values from props")

            assertTrue(jenkins.lastSh.contains('--chart-tarball  "/tmp/artifacts/helm/props-chart-1.2.3.tgz"'),
                    "Should use chart name derived from moduleProps")
        } finally {
            HelmChartUtil.metaClass = originalMetaClass
        }
    }

    @Test
    void testHelmReleaseConflictingConfiguration() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test_app'

        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Both chartName/chartTarBallFile are set in config, but moduleProps.chartDirectory is also provided'),
                "Should throw exception for conflicting configuration")
    }

    @Test
    void testHelmReleaseMissingChartInformation() {
        config.helmChartModule = null
        config.chartTarBallFile = null
        config.chartName = null

        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Failed to determine required helm chart information'),
                "Should throw exception for missing chart information")
    }

    @Test
    void testHelmReleaseWithMissingPropertyException() {
        def configWithException = new MockConfig([
            jenkins: jenkins,
            env: 'dev',
            region: 'us-east-1',
            projectVersion: '1.2.3',
            projectName: 'testproj',
            chartTarBallFile: new File('/tmp/chart.tgz'),
            chartName: 'test_app',
        ]) {
            boolean hasProperty(String name) {
                if (name == 'helmChartModule') {
                    throw new MissingPropertyException("helmChartModule property not found")
                }
                return super.hasProperty(name)
            }
        }

        assertThrows(MissingPropertyException.class) {
            helmReleaseStage.helmRelease(configWithException)
        }
    }

    @Test
    void testHelmReleaseMultipleEnvironments() {
        ['dev', 'staging', 'prd'].each { env ->
            config.env = env
            helmReleaseStage.helmRelease(config)

            def yacliCommand = jenkins.allShCommands.find { it.contains('yacli helm deploy') }
            assertTrue(yacliCommand.contains("--tarball-values-file values/${env}.yaml"),
                    "Should use correct environment values file for ${env}")
            assertTrue(jenkins.DEPLOY_STEP.contains("Helm Release / Deployment - ${env}"),
                    "Should set correct DEPLOY_STEP for ${env}")

            // Reset for next iteration
            jenkins.allShCommands.clear()
        }
    }

    @Test
    void testHelmReleaseMultipleRegions() {
        [
            'us-east-1',
            'us-west-2',
            'eu-west-1',
        ].each { region ->
            config.region = region
            helmReleaseStage.helmRelease(config)

            def yacliCommand = jenkins.allShCommands.find { it.contains('yacli helm deploy') }
            def expectedRegionCode = jenkins.REGIONS[region]
            assertTrue(yacliCommand.contains("--tarball-values-file values/dev-${expectedRegionCode}.yaml"),
                    "Should use correct region values file for ${region}")
            assertTrue(jenkins.DEPLOY_STEP.contains("Helm Release / Deployment - dev ${region}"),
                    "Should set correct DEPLOY_STEP for ${region}")

            // Reset for next iteration
            jenkins.allShCommands.clear()
        }
    }

    @Test
    void testHelmReleaseAwsAccountAndClusterIntegration() {
        def originalMetaClass = HelmChartUtil.metaClass
        HelmChartUtil.metaClass.static.resolveAccountId = { ModuleConfig c, def p -> '123456789' }
        HelmChartUtil.metaClass.static.buildClusterName = { ModuleConfig c, def p -> 'my-cluster' }

        try {
            helmReleaseStage.helmRelease(config)

            def hasAwsMessage = jenkins.allEchoMessages.any {
                it.contains('Deploy using Cluster: my-cluster')
            }
            assertTrue(hasAwsMessage, "Should echo AWS account and cluster information")

            // The aws eks update-kubeconfig command is commented out in HelmReleaseStage
            // so we don't expect to find it in the commands
            def kubeconfigCommand = jenkins.allShCommands.find {
                it.contains('aws eks') && it.contains('update-kubeconfig')
            }
            // Since the command is commented out, kubeconfigCommand should be null
            assertTrue(kubeconfigCommand == null, "Should not execute aws eks update-kubeconfig command as it's commented out")
        } finally {
            HelmChartUtil.metaClass = originalMetaClass
        }
    }

    @Test
    void testHelmReleaseYacliDeployCommandParameters() {
        // Mock HelmChartUtil to return predictable values
        def originalMetaClass = HelmChartUtil.metaClass
        HelmChartUtil.metaClass.static.resolveAccountId = { ModuleConfig c, def p -> '123456789' }
        HelmChartUtil.metaClass.static.buildClusterName = { ModuleConfig c, def p -> 'test-cluster-name' }

        try {
            helmReleaseStage.helmRelease(config)

            def yacliCommand = jenkins.allShCommands.find { it.contains('yacli helm deploy') }
            assertTrue(yacliCommand != null, "Should have yacli helm deploy command")

            // Check all required parameters
            assertTrue(yacliCommand.contains('--artifactory-user user'), "Should use correct artifactory user")
            assertTrue(yacliCommand.contains('--artifactory-password pass'), "Should use correct artifactory password")
            assertTrue(yacliCommand.contains('--skip-init'), "Should include skip-init flag")
            assertTrue(yacliCommand.contains('--verbose'), "Should include verbose flag")
            assertTrue(yacliCommand.contains('--group group'), "Should include group")
            assertTrue(yacliCommand.contains('--set global.version=1.2.3'), "Should set global version")
            assertTrue(yacliCommand.contains('--ignore-missing-value-files'), "Should include ignore-missing-value-files flag")
            assertTrue(yacliCommand.contains('--release-name testproj'), "Should include release name")
            assertTrue(yacliCommand.contains('--cluster-name test-cluster-name'), "Should include cluster name")
            assertTrue(yacliCommand.contains('--force-namespace default'), "Should include namespace")
        } finally {
            HelmChartUtil.metaClass = originalMetaClass
        }
    }

    @Test
    void testHelmReleaseWithDifferentProjectVersions() {
        [
            '1.0.0',
            '2.1.3-beta',
            '3.0.0-SNAPSHOT',
        ].each { version ->
            config.projectVersion = version
            helmReleaseStage.helmRelease(config)

            def yacliCommand = jenkins.allShCommands.find { it.contains('yacli helm deploy') }
            assertTrue(yacliCommand.contains("--set global.version=${version}"),
                    "Should set correct global version for ${version}")
            assertTrue(jenkins.DEPLOY_STEP.contains("Helm Release / Deployment - dev us-east-1 ${version}"),
                    "Should include version in DEPLOY_STEP for ${version}")

            // Reset for next iteration
            jenkins.allShCommands.clear()
        }
    }

    @Test
    void testHelmReleaseWithPropsChartDirectoryOnly() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = null  // No existing file
        config.chartName = null  // No existing name

        def originalMetaClass = HelmChartUtil.metaClass
        HelmChartUtil.metaClass.static.validateChartDirectory = { ModuleConfig c, File dir -> }
        HelmChartUtil.metaClass.static.getChartName = { ModuleConfig c, File dir -> 'props-chart' }

        try {
            helmReleaseStage.helmRelease(config)

            // Should derive values from props when config values are null
            assertTrue(jenkins.lastSh.contains('--chart-name props-chart'),
                    "Should use chart name derived from props")
            assertTrue(jenkins.lastSh.contains('--chart-tarball  "/tmp/artifacts/helm/props-chart-1.2.3.tgz"'),
                    "Should use chart tarball path derived from props")
        } finally {
            HelmChartUtil.metaClass = originalMetaClass
        }
    }

    @Test
    void testHelmReleaseWithPropsChartDirectoryAndConfigValues() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/existing.tgz')  // Set existing file
        config.chartName = 'existing-chart'  // Set existing name

        // This should throw an exception due to conflicting configuration
        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Both chartName/chartTarBallFile are set in config, but moduleProps.chartDirectory is also provided'),
                "Should throw exception for conflicting configuration")
    }

    @Test
    void testHelmReleaseValueFilesGeneration() {
        config.env = 'production'
        config.region = 'eu-west-1'
        helmReleaseStage.helmRelease(config)

        def yacliCommand = jenkins.allShCommands.find { it.contains('yacli helm deploy') }
        assertTrue(yacliCommand.contains('--tarball-values-file values/production.yaml'),
                "Should include environment values file")
        assertTrue(yacliCommand.contains('--tarball-values-file values/production-euw1.yaml'),
                "Should include environment-region values file")
    }

    @Test
    void testHelmReleaseWithNullConfig() {
        // Test config?.hasProperty() when config could be null
        def stage = new HelmReleaseStage()

        assertThrows(Exception.class) {
            stage.helmRelease(null)
        }
    }

    @Test
    void testHelmReleaseConfigWithoutHelmChartModuleProperty() {
        // Test config without helmChartModule property
        def configWithoutProperty = new MockConfig([
            jenkins: jenkins,
            env: 'dev',
            region: 'us-east-1',
            projectVersion: '1.2.3',
            projectName: 'testproj',
        ]) {
            boolean hasProperty(String name) {
                if (name == 'helmChartModule') {
                    return false  // Simulate missing property
                }
                return super.hasProperty(name)
            }
        }

        // Should throw exception due to missing chart information
        assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(configWithoutProperty)
        }
    }

    @Test
    void testHelmReleaseConfigWithoutChartTarBallFileProperty() {
        config.helmChartModule = null

        def configWithoutProperty = new MockConfig([
            jenkins: jenkins,
            env: 'dev',
            region: 'us-east-1',
            projectVersion: '1.2.3',
            projectName: 'testproj',
        ]) {
            boolean hasProperty(String name) {
                if (name == 'chartTarBallFile') {
                    return false  // Simulate missing property
                }
                return super.hasProperty(name)
            }
        }

        // Should throw exception due to missing chart information
        assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(configWithoutProperty)
        }
    }

    @Test
    void testHelmReleaseConfigWithoutChartNameProperty() {
        config.helmChartModule = null

        def configWithoutProperty = new MockConfig([
            jenkins: jenkins,
            env: 'dev',
            region: 'us-east-1',
            projectVersion: '1.2.3',
            projectName: 'testproj',
        ]) {
            boolean hasProperty(String name) {
                if (name == 'chartName') {
                    return false  // Simulate missing property
                }
                return super.hasProperty(name)
            }
        }

        // Should throw exception due to missing chart information
        assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(configWithoutProperty)
        }
    }

    @Test
    void testHelmReleaseConfigWithoutDeploymentTimeoutProperty() {
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'

        def configWithoutProperty = new MockConfig([
            jenkins: jenkins,
            env: 'dev',
            region: 'us-east-1',
            projectVersion: '1.2.3',
            projectName: 'testproj',
            chartTarBallFile: new File('/tmp/chart.tgz'),
            chartName: 'test-chart',
        ]) {
            boolean hasProperty(String name) {
                if (name == 'deploymentTimeout') {
                    return false  // Simulate missing property
                }
                return super.hasProperty(name)
            }
        }

        helmReleaseStage.helmRelease(configWithoutProperty)

        // Should use default timeout
        assertTrue(jenkins.lastSh.contains('--helm-timeout 5m0s'), "Should use default timeout")
    }

    @Test
    void testHelmReleaseWithOnlyChartNameInConfig() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = null
        config.chartName = 'existing-chart-name'  // Only chartName exists

        // Should throw exception due to conflicting configuration
        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Both chartName/chartTarBallFile are set in config, but moduleProps.chartDirectory is also provided'),
                "Should throw exception for conflicting configuration")
    }

    @Test
    void testHelmReleaseWithOnlyChartTarBallFileInConfig() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/existing.tgz')  // Only chartTarBallFile exists
        config.chartName = null

        // Should throw exception due to conflicting configuration
        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Both chartName/chartTarBallFile are set in config, but moduleProps.chartDirectory is also provided'),
                "Should throw exception for conflicting configuration")
    }

    @Test
    void testHelmReleaseWithPropsChartDirectoryAndExistingChartName() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = null
        config.chartName = 'existing-chart'  // chartName already exists

        // Should throw exception due to conflicting configuration
        // (chartName exists in config AND moduleProps.chartDirectory exists)
        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Both chartName/chartTarBallFile are set in config, but moduleProps.chartDirectory is also provided'),
                "Should throw exception for conflicting configuration")
    }

    @Test
    void testHelmReleaseWithPropsChartDirectoryAndExistingChartTarBallFile() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/existing.tgz')  // chartTarBallFile already exists
        config.chartName = null

        // Should throw exception due to conflicting configuration
        // (chartTarBallFile exists in config AND moduleProps.chartDirectory exists)
        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Both chartName/chartTarBallFile are set in config, but moduleProps.chartDirectory is also provided'),
                "Should throw exception for conflicting configuration")
    }

    @Test
    void testHelmReleaseWithConfigDeploymentTimeoutOnly() {
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'
        config.deploymentTimeout = '15m0s'  // Config has timeout

        helmReleaseStage.helmRelease(config)

        // Should use config timeout, not props or default
        assertTrue(jenkins.lastSh.contains('--helm-timeout 15m0s'), "Should use config timeout")
    }

    @Test
    void testHelmReleaseWithPropsDeploymentTimeoutFallback() {
        def props = new ModuleProps([deploymentTimeout: '25m0s'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'
        config.deploymentTimeout = null  // No config timeout

        helmReleaseStage.helmRelease(config)

        // Should use props timeout, not default
        assertTrue(jenkins.lastSh.contains('--helm-timeout 25m0s'), "Should use props timeout")
    }

    @Test
    void testHelmReleaseWithNullPropsAndNoConfigTimeout() {
        helmReleaseStage = new HelmReleaseStage()  // null props
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'
        config.deploymentTimeout = null  // No config timeout

        helmReleaseStage.helmRelease(config)

        // Should use default timeout
        assertTrue(jenkins.lastSh.contains('--helm-timeout 5m0s'), "Should use default timeout")
    }

    @Test
    void testHelmReleaseMissingChartNameOnly() {
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')  // Has file
        config.chartName = null  // Missing name

        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Failed to determine required helm chart information'),
                "Should throw exception for missing chart name")
    }

    @Test
    void testHelmReleaseMissingChartTarBallFileOnly() {
        config.helmChartModule = null
        config.chartTarBallFile = null  // Missing file
        config.chartName = 'test-chart'  // Has name

        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(config)
        }

        assertTrue(exception.getMessage().contains('Failed to determine required helm chart information'),
                "Should throw exception for missing chart file")
    }

    @Test
    void testHelmReleaseWithNonBranchDeploy() {
        config.isBranchDeploy = false  // Explicit false
        helmReleaseStage.helmRelease(config)

        def yacliCommand = jenkins.allShCommands.find { it.contains('yacli helm deploy') }

        // Should use config namespace, not generated
        assertTrue(yacliCommand.contains('--force-namespace default'), "Should use config namespace")

        // Should not have branch namespace message
        def hasBranchNamespaceMessage = jenkins.allEchoMessages.any {
            it.contains('HelmReleaseStage: generated branch namespace')
        }
        assertTrue(!hasBranchNamespaceMessage, "Should not echo branch namespace message")
    }

    @Test
    void testHelmReleaseWithEmptyPropsChartDirectory() {
        def props = new ModuleProps([chartDirectory: null])  // null chartDirectory in props
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'

        helmReleaseStage.helmRelease(config)

        // Should not echo the props derivation message
        def hasDerivingMessage = jenkins.allEchoMessages.any {
            it.contains('config.chartName is null and try to derive values from props')
        }
        assertTrue(!hasDerivingMessage, "Should not echo derivation message when moduleProps.chartDirectory is null")
    }

    @Test
    void testHelmReleaseWithPropsButNoChartDirectory() {
        def props = new ModuleProps([deploymentTimeout: '30m0s'])  // props without chartDirectory
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'

        helmReleaseStage.helmRelease(config)

        // Should use props timeout but not try to derive chart info
        assertTrue(jenkins.lastSh.contains('--helm-timeout 30m0s'), "Should use props timeout")

        def hasDerivingMessage = jenkins.allEchoMessages.any {
            it.contains('config.chartName is null and try to derive values from props')
        }
        assertTrue(!hasDerivingMessage, "Should not echo derivation message when no chartDirectory")
    }

    @Test
    void testHelmReleaseWithComplexConfigPropertyCombinations() {
        // Test various combinations of config property existence
        config.helmChartModule = null

        def configWithMixedProperties = new MockConfig([
            jenkins: jenkins,
            env: 'dev',
            region: 'us-east-1',
            projectVersion: '1.2.3',
            projectName: 'testproj',
            chartName: 'mixed-chart',
        ]) {
            boolean hasProperty(String name) {
                // chartName exists, chartTarBallFile doesn't, deploymentTimeout doesn't
                if (name == 'chartName') {
                    return true
                }
                if (name == 'chartTarBallFile') {
                    return false
                }
                if (name == 'deploymentTimeout') {
                    return false
                }
                return super.hasProperty(name)
            }
        }

        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(configWithMixedProperties)
        }

        assertTrue(exception.getMessage().contains('Failed to determine required helm chart information'),
                "Should throw exception when missing required chart file")
    }

    @Test
    void testHelmReleasePropsChartDirectoryWithExistingChartNameElvis() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage = new HelmReleaseStage()
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = null
        config.chartName = null  // Both null, so will derive from props

        def originalMetaClass = HelmChartUtil.metaClass
        HelmChartUtil.metaClass.static.validateChartDirectory = { ModuleConfig c, File dir -> }
        HelmChartUtil.metaClass.static.getChartName = { ModuleConfig c, File dir -> 'props-derived-chart' }

        try {
            helmReleaseStage.helmRelease(config)

            // Should derive chartName from props since config.chartName is null
            assertTrue(jenkins.lastSh.contains('--chart-name props-derived-chart'),
                    "Should use chartName derived from props")
            assertTrue(jenkins.lastSh.contains('--chart-tarball  "/tmp/artifacts/helm/props-derived-chart-1.2.3.tgz"'),
                    "Should use chartTarBallFile derived from props")
        } finally {
            HelmChartUtil.metaClass = originalMetaClass
        }
    }

    @Test
    void testHelmReleasePropsChartDirectoryWithPartialExistingValues() {
        def props = new ModuleProps([chartDirectory: '/tmp/chart-dir'])
        helmReleaseStage = new HelmReleaseStage()
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = null
        config.chartName = null  // Both null initially

        // Now set chartName after props setup to test elvis operator
        def originalMetaClass = HelmChartUtil.metaClass
        HelmChartUtil.metaClass.static.validateChartDirectory = { ModuleConfig c, File dir -> }
        HelmChartUtil.metaClass.static.getChartName = { ModuleConfig c, File dir -> 'props-derived-chart' }

        try {
            // Simulate the case where chartName gets set during derivation
            helmReleaseStage.helmRelease(config)

            // Should derive both values from props
            assertTrue(jenkins.lastSh.contains('--chart-name props-derived-chart'),
                    "Should use chartName derived from props")
            assertTrue(jenkins.lastSh.contains('--chart-tarball  "/tmp/artifacts/helm/props-derived-chart-1.2.3.tgz"'),
                    "Should use chartTarBallFile derived from props")
        } finally {
            HelmChartUtil.metaClass = originalMetaClass
        }
    }

    @Test
    void testHelmReleaseElvisOperatorForDeploymentTimeoutChain() {
        // Test the chain: config.deploymentTimeout ?: moduleProps.deploymentTimeout ?: '5m0s'
        def props = new ModuleProps([deploymentTimeout: '12m0s'])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'
        config.deploymentTimeout = null  // null in config, should use props

        helmReleaseStage.helmRelease(config)

        assertTrue(jenkins.lastSh.contains('--helm-timeout 12m0s'),
                "Should use props timeout when config timeout is null")
    }

    @Test
    void testHelmReleaseElvisOperatorForDeploymentTimeoutDefault() {
        // Test the chain when both config and props are null: ?: '5m0s'
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'
        config.deploymentTimeout = null  // null in config

        helmReleaseStage.helmRelease(config)

        assertTrue(jenkins.lastSh.contains('--helm-timeout 5m0s'),
                "Should use default timeout when both config and props are null")
    }

    @Test
    void testHelmReleaseElvisOperatorForDeploymentTimeoutPropsNull() {
        // Test when props exists but deploymentTimeout is null
        def props = new ModuleProps([deploymentTimeout: null, chartDirectory: null])
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'
        config.deploymentTimeout = null  // null in config

        helmReleaseStage.helmRelease(config)

        assertTrue(jenkins.lastSh.contains('--helm-timeout 5m0s'),
                "Should use default timeout when moduleProps.deploymentTimeout is null")
    }

    @Test
    void testHelmReleaseConfigHasPropertyFalseForAllProperties() {
        // Test when config.hasProperty returns false for all properties
        config.helmChartModule = null

        def configWithNoProperties = new MockConfig([
            jenkins: jenkins,
            env: 'dev',
            region: 'us-east-1',
            projectVersion: '1.2.3',
            projectName: 'testproj',
        ]) {
            boolean hasProperty(String name) {
                // All properties return false
                if (name in [
                            'chartTarBallFile',
                            'chartName',
                            'deploymentTimeout',
                        ]) {
                    return false
                }
                return super.hasProperty(name)
            }
        }

        Exception exception = assertThrows(Exception.class) {
            helmReleaseStage.helmRelease(configWithNoProperties)
        }

        assertTrue(exception.getMessage().contains('Failed to determine required helm chart information'),
                "Should throw exception when no properties exist")
    }

    @Test
    void testHelmReleasePropsChartDirectoryNullBranch() {
        // Test the condition: this.props?.chartDirectory != null when it's null
        def props = new ModuleProps([chartDirectory: null])
        helmReleaseStage = new HelmReleaseStage()
        helmReleaseStage.setModuleProps(props)
        config.helmChartModule = null
        config.chartTarBallFile = new File('/tmp/chart.tgz')
        config.chartName = 'test-chart'

        helmReleaseStage.helmRelease(config)

        // Should not echo the derivation message since moduleProps.chartDirectory is null
        def hasDerivingMessage = jenkins.allEchoMessages.any {
            it.contains('config.chartName is null and try to derive values from props')
        }
        assertTrue(!hasDerivingMessage, "Should not echo derivation message when moduleProps.chartDirectory is null")
    }
}
