package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.metadata.HelmChartUtil

/**
 * Stage responsible for handling Helm release deployments in the pipeline.
 */
class HelmReleaseStage extends AbstractStage {

    @Override
    void initializeStage() {
        runner = this.&helmRelease
    }

    void helmRelease(ModuleConfig config) {
        // Validate chart directory and pull chart using HelmChartModule methods
        // These methods must be called on the HelmChartModule instance, so we expect
        // the caller to have already validated and pulled the chart.
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        def chartModule = config?.hasProperty('helmChartModule') ? config.helmChartModule : null

        File chartTarBallFile
        String chartName
        String deploymentTimeout
        if (chartModule) {
            chartModule.validateChartDirectory(config)
            chartModule.pullChart(config)
            chartTarBallFile = chartModule.getChartTarBallFile(config)
            chartName = chartModule.getChartName(config)
            deploymentTimeout = chartModule.deploymentTimeout
        } else {
            // Fallback logic when no chartModule is available
            jenkins.echo("Using fallback logic for helm release configuration")

            // Step 1: Try to get values from config using safe property access
            chartTarBallFile = config?.hasProperty('chartTarBallFile') ? config.chartTarBallFile : null
            chartName = config?.hasProperty('chartName') ? config.chartName : null
            deploymentTimeout = config?.hasProperty('deploymentTimeout') ? config.deploymentTimeout : null

            // Step 2: Validate chart configuration
            if ((chartName || chartTarBallFile) && this.moduleProps?.chartDirectory) {
                throw new IllegalArgumentException("Both chartName/chartTarBallFile are set in config, but moduleProps.chartDirectory is also provided. Please ensure only one source of truth is used for helm chart configuration.")
            }

            // Step 3: Try to derive missing values from moduleProps if available
            if ( this.moduleProps?.chartDirectory != null) {
                jenkins.echo("config.chartName is null and try to derive values from moduleProps")
                File chartDirectory = new File(this.moduleProps.chartDirectory as String)
                HelmChartUtil.validateChartDirectory(config, chartDirectory)
                chartName = chartName ?: HelmChartUtil.getChartName(config, chartDirectory)
                chartTarBallFile = chartTarBallFile ?: new File("${jenkins.env.WORKSPACE}/artifacts/helm/${chartName}-${config.projectVersion}.tgz")

            }

            // Set default timeout if still null
            deploymentTimeout = deploymentTimeout ?: this.moduleProps?.deploymentTimeout ?: '5m0s'

            // Final validation
            if (!chartName || !chartTarBallFile) {
                throw new Exception("Failed to determine required helm chart information: chartName=${chartName}, chartTarBallFile=${chartTarBallFile}")
            }
        }
        jenkins.DEPLOY_STEP = "Helm Release / Deployment - ${config.env} ${config.region} ${projectVersion}"
        String namespace = config.namespace
        if (config.isBranchDeploy) {
            namespace = jenkins.k8s.generateNamespaceName()
            jenkins.echo("HelmReleaseStage: generated branch namespace = ${namespace}")
        }

        // Build cluster name using the shared utility method
        def clusterName = HelmChartUtil.buildClusterName(config, this.moduleProps)

        jenkins.echo("Deploy using Cluster: ${clusterName}")

        jenkins.sh """yacli helm deploy \\
                --artifactory-user ${jenkins.env.ARTIFACTORY_USER} \\
                --artifactory-password ${jenkins.env.ARTIFACTORY_PASSWORD} \\
                --set global.version=${config.projectVersion} \\
                --skip-init \\
                --ignore-missing-value-files \\
                --tarball-values-file values/${config.env}.yaml \\
                --tarball-values-file values/${config.env}-${jenkins.REGIONS[config.region]}.yaml \\
                --chart-tarball  \"${chartTarBallFile}\" \\
                --chart-name ${chartName} \\
                --release-name ${config.projectName} \\
                --cluster-name ${clusterName} \\
                --verbose \\
                --force-namespace ${namespace} \\
                --helm-timeout ${deploymentTimeout} \\
                --group ${jenkins.env.GROUP}
                """
    }
}
