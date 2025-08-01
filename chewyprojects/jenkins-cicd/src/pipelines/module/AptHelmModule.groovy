package pipelines.module

import groovy.transform.builder.Builder
import pipelines.PipelineConfig
import pipelines.module.stage.AptKubernetesRegionDeployStage
import pipelines.module.stage.AptKubernetesRegionRollbackStage
import pipelines.module.stage.KubernetesRegionPostDeployStage

class AptHelmModule extends Module {
    String namespace
    String featureDeployHelmName
    String hostPath
    String serviceModuleName
    String helmChartSuffix
    String helmTimeOut

    @Builder
    AptHelmModule(String namespace, String featureDeployHelmName, String hostPath,
    String serviceModuleName, String helmChartSuffix, String helmTimeOut) {
        super([name: 'aptHelm', dockerImage: ModuleProps.CI_BASE_BUILD_IMAGE])
        this.namespace = namespace ?: null
        this.featureDeployHelmName = featureDeployHelmName ?: null
        this.hostPath = hostPath ?: null
        this.serviceModuleName = serviceModuleName ?: null
        this.helmChartSuffix = helmChartSuffix ?: null
        this.helmTimeOut = helmTimeOut ?: '5m0s'
    }

    AptHelmModule(PipelineConfig config, props) {
        super(props + [dockerImage: ModuleProps.CI_BASE_BUILD_IMAGE])
        this.namespace = props.namespace ?: null
        this.featureDeployHelmName = props.featureDeployHelmName ?: null
        this.hostPath = props.hostPath ?: null
        this.serviceModuleName = props.serviceModuleName ?: null
        this.helmChartSuffix = props.helmChartSuffix ?: null
        this.helmTimeOut = props.helmTimeOut ?: '5m0s'
    }

    void packageChart(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        def chartDir

        // TODO: remove this prefix once helm dir is in root dir for rxp repos.
        if(serviceModuleName) {
            chartDir = "${jenkins.env.WORKSPACE}/${serviceModuleName}/helm"
        } else {
            chartDir = "${jenkins.env.WORKSPACE}/helm"
        }

        if (helmChartSuffix) {
            chartDir = chartDir + "/" + helmChartSuffix
        }
        jenkins.arti.helmInit()
        jenkins.arti.helmPackageWithYacli(chartDir, projectVersion, projectVersion)
    }

    void publishChart(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def previousVersion = config.previousVersion

        if (previousVersion) {
            packageChart(config)
        }

        try {
            jenkins.arti.publish("${jenkins.env.WORKSPACE}/artifacts/helm/${projectName}-${projectVersion}.tgz", projectVersion)
        } catch (Exception ex) {
            // it has intermittent errors during helm build clean step,
            // ignores the error and continue with the deployment.
            if(ex.getMessage().contains("Could not locate artifact")) {
                jenkins.echo("Failed to publish helm chart: ${ex.getMessage()}")
            }
            throw ex
        }
    }

    void pullChart(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def filename = "artifacts/helm/${projectName}-${projectVersion}.tgz"

        if (!jenkins.fileExists(filename)) {
            jenkins.arti.pull('helm', projectName, projectVersion, false)
            // Move helm chart into `artifacts/helm/` directory for AptKubernetesRegionDeployStage
            // TODO: update to avoid conflicting with HelmModule chartFilename
            jenkins.sh """#!/bin/bash
                mkdir -p artifacts/helm/
                mv ${projectName}-${projectVersion}.tgz artifacts/helm/
            """
        }
    }

    protected static Boolean isNotBranchDeploy(ModuleConfig config) {
        return !config.isBranchDeploy
    }

    @Override
    protected void initializeStages() {
        build = Stage.builder().runner(this.&packageChart).build()
        publish = Stage.builder().runner(this.&publishChart).shouldRun(this.&isNotBranchDeploy).build()
        globalDeploy = Stage.builder().runner(this.&pullChart).shouldRun(this.&isNotBranchDeploy).build()
        regionDeploy = new AptKubernetesRegionDeployStage(namespace, featureDeployHelmName, hostPath, helmTimeOut)
        regionPostDeploy = new KubernetesRegionPostDeployStage('apt-shared')
        regionRollback = new AptKubernetesRegionRollbackStage(namespace, helmTimeOut)
    }
}
