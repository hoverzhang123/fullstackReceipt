package pipelines.module

import groovy.transform.builder.Builder
import pipelines.PipelineConfig
import pipelines.module.stage.HelmReleaseStage
import pipelines.module.stage.HelmRollbackStage
import pipelines.metadata.HelmChartUtil

/** The HelmChartModule is a module that handles the lifecycle of a Helm chart.
 * It includes stages for building, validating, publishing, and deploying the chart.
 * It is designed to work with Jenkins pipelines and integrates with Artifactory for
 * storing and retrieving helm charts.
 */
class HelmChartModule extends Module {

    // Path to the directory container the `Chart.yaml` file.
    protected File chartDirectory
    // Path to the `Chart.yaml` file within chartDirectory,
    protected File chartYamlFile
    // The timeout for the helm deploy (i.e. how long the deploy has to finish)
    // This may need to be raised for applications with a lot of pods.
    protected String deploymentTimeout

    /**
     * CONSTRUCTOR
     * @param chartDirectory The path to the directory containing the `Chart.yaml` file.
     * @param deploymentTimeout The timeout for the helm deploy (i.e. how long the deploy has to finish).
     * Default "5m0s".
     */
    @Builder
    HelmChartModule(String chartDirectory, String deploymentTimeout) {
        super([name: 'HelmChart', dockerImage: ModuleProps.YACLI_IMAGE])
        chartDirectory = chartDirectory.replace('\\', '/').trim()
        this.chartDirectory = new File(chartDirectory)
        this.chartYamlFile = new File(this.chartDirectory, 'Chart.yaml')
        this.deploymentTimeout = deploymentTimeout ?: '5m0s'
    }

    HelmChartModule(PipelineConfig config, props) {
        super(props + [dockerImage: ModuleProps.YACLI_IMAGE])
        this.chartDirectory = new File(props.chartDirectory as String)
        this.chartYamlFile = new File(this.chartDirectory, 'Chart.yaml')
        this.deploymentTimeout = props.deploymentTimeout as String
    }

    /**
     * Initializes the stages of the module. All non-specialized stages default to `EmptyStage`s.
     */
    @Override
    protected void initializeStages() {
        build = Stage.builder().runner(this.&packageChart).build()
        validate = Stage.builder().runner(this.&validateChart).build()
        publish = Stage.builder().runner(this.&publishChart).build()
        // Leveraging the fact that the global deploy stage is run before the region deploy stage,
        // we can use the global deploy stage to pull the chart.
        globalDeploy = Stage.builder().runner(this.&pullChart).build()
        regionDeploy = new HelmReleaseStage()
        regionRollback = new HelmRollbackStage()
    }

    /**
     * Used by the "build" stage to package the helm chart. Packages the helmchart directory
     * into a local tarball.
     * @param config The module configuration object.
     */
    protected void packageChart(ModuleConfig config) {
        HelmChartUtil.validateChartDirectory(config, this.chartDirectory)
        config.jenkins.sh ([
            "#!/bin/bash",
            "set -e",
            "helm repo add chewy https://chewyinc.jfrog.io/artifactory/api/helm/helm-virtual --username \"${config.jenkins.env.ARTIFACTORY_USER}\" --password \"${config.jenkins.env.ARTIFACTORY_PASSWORD}\"",
            "helm repo update",
        ].join("\n"))
        config.jenkins.arti.helmPackage(
                this.chartDirectory.toString(), // packageRootDirectory
                config.projectVersion, // appVersion
                config.projectVersion // chartVersion
                )
    }

    /**
     * Used by the "validate" stage to validate the helm chart. Currently unused but
     * should use APT's CLI to validate the chart.
     * @param config The module configuration object.
     */
    protected void validateChart(ModuleConfig config) {
        config.jenkins.echo("No helm chart validations currently exist, skipping.")
    }

    /**
     * Used by the "publish" stage to publish the helm chart to Artifactory.
     * The tarball should exist locally via the packageChart step.
     * @param config The module configuration object.
     */
    protected void publishChart(ModuleConfig config) {
        HelmChartUtil.validateChartDirectory(config, this.chartDirectory)
        // Branch deploys don't push their helm charts, they
        // just deploy them from local.
        if (config.isBranchDeploy) {
            config.jenkins.echo("Skipping helm chart publish step for branch deploy.")
            return
        }

        File chartTarBallFile = this.getChartTarBallFile(config)
        def fileExists = config.jenkins.sh(script: "[ -f '${chartTarBallFile}' ] && echo 'yes' || echo 'no'", returnStdout: true).trim()
        if (fileExists != "yes") {
            throw new IllegalArgumentException("Chart tarball file ${chartTarBallFile} does not exist.")
        }
        config.jenkins.arti.publish(chartTarBallFile.toString(), config.projectVersion)
    }

    /**
     * Used by the "globalDeploy" stage to pull the helm chart from Artifactory if
     * it does not exist locally.
     * @param config The module configuration object.
     */
    protected void pullChart(ModuleConfig config) {
        HelmChartUtil.validateChartDirectory(config, this.chartDirectory)
        File chartTarBallFile = this.getChartTarBallFile(config)
        def fileExists = config.jenkins.sh(script: "[ -f '${chartTarBallFile}' ] && echo 'yes' || echo 'no'", returnStdout: true).trim()
        if (fileExists == "yes") {
            config.jenkins.echo("Chart tarball file ${chartTarBallFile} exists. Skipping pull from Artifactory.")
        } else {
            config.jenkins.echo("Chart tarball file ${chartTarBallFile} does not exist. Pulling from Artifactory.")

            config.jenkins.arti.pull(
                    'helm', // platform
                    HelmChartUtil.getChartName(config, this.chartDirectory), // artifactName
                    config.projectVersion, // versionNumber
                    false // explode
                    )
            // Moving the downloaded chart (in the current workdir) into the artifacts directory where it would have been built.
            config.jenkins.sh ([
                "#!/bin/bash",
                "set -e",
                "mkdir -p artifacts/helm/",
                "mv ${chartTarBallFile.name} ${chartTarBallFile.parentFile}",
            ].join("\n"))
        }
    }

    /**
     * Returns the tarball file (path) where the packaged helm chart should be found.
     * @param config The module configuration object.
     * @return The tarball file (path) of the helm chart tarball.
     */
    private File getChartTarBallFile(ModuleConfig config) {
        String chartName = HelmChartUtil.getChartName(config, this.chartDirectory)
        return new File("${config.jenkins.env.WORKSPACE}/artifacts/helm/${chartName}-${config.projectVersion}.tgz")
    }

}
