package pipelines.metadata

import pipelines.module.ModuleConfig

/**
 * Utility class for Helm Chart related operations.
 * Provides common functionality used across modules.
 */
class HelmChartUtil {

    /**
     * Validates that the chart directory exists and contains a Chart.yaml file.
     * @param config The configuration object containing jenkins
     * @param chartDirectory The chart directory as File object
     * @throws Exception if the chart directory is invalid or missing Chart.yaml
     */
    static void validateChartDirectory(ModuleConfig config, File chartDirectory) {
        File chartYamlFile = new File(chartDirectory, 'Chart.yaml')
        // NOTE: trying to do the directory checks with the "File" objects had a bunch of
        // issues and always said "didn't exist". Thus, we are using the shell script to do the checks.
        String validDir = config.jenkins.sh(script: """
            #!/bin/bash
            set -e
            if [ -d '${chartDirectory}' ] && [ -f '${chartYamlFile}' ]; then
                echo 'yes'
            else
                echo 'no'
            fi
        """, returnStdout: true).trim()

        if (validDir != "yes") {
            throw new IllegalArgumentException("chartDirectory='${chartDirectory}' is invalid or missing Chart.yaml!")
        }
    }

    /**
     * Returns the name of the helm chart from the name field in Chart.yaml file.
     * @param config The configuration object containing jenkins
     * @param chartDirectory The chart directory as File object
     * @return The name of the helm chart
     * @throws Exception if Chart.yaml doesn't contain a name field
     */
    static String getChartName(ModuleConfig config, File chartDirectory) {
        File chartYamlFile = new File(chartDirectory, 'Chart.yaml')
        def chartYamlText = config.jenkins.readYaml(file: chartYamlFile.path)
        String chartName = chartYamlText.name
        if (!chartName) {
            throw new IllegalArgumentException("chartYamlFile='${chartYamlFile}' does not contain a 'name' field.")
        }
        return chartName
    }

    /**
     * Resolves the AWS account ID based on configuration and properties.
     * @param config The module configuration object containing jenkins and environment info
     * @param props The properties object containing custom AWS account configurations
     * @return The resolved AWS account ID
     * @throws Exception if the environment is not found in custom AWS accounts configuration
     */
    static String resolveAccountId(ModuleConfig config, def props) {
        def jenkins = config.jenkins
        def accountId = jenkins.ACCOUNTS[config.env]

        if (props?.awsAccounts && props.awsAccounts[config.env]) {
            accountId = props.awsAccounts[config.env]
            jenkins.echo("Using custom account for environment '${config.env}': ${accountId}")
        }

        return accountId
    }

    /**
     * Builds and returns the cluster name based on configuration and properties.
     * @param config The configuration object containing env and region
     * @param props The properties object that may contain custom cluster configurations
     * @return The constructed cluster name
     */
    static String buildClusterName(ModuleConfig config, def props) {
        def jenkins = config.jenkins

        if (props?.cluster?.market && props?.cluster?.clusterType && props?.cluster?.clusterSuffix) {
            // Custom cluster format: {market}-{env}-{region}-{clusterType}-{suffix}
            def market = props.cluster.market
            def clusterType = props.cluster.clusterType
            def suffix = props.cluster.clusterSuffix
            return "${market}-${config.env}-${jenkins.REGIONS[config.region]}-${clusterType}-${suffix}"
        } else {
            // Default/legacy format: {env}-{region}-apt-shared
            return "${config.env}-${jenkins.REGIONS[config.region]}-apt-shared"
        }
    }
}
