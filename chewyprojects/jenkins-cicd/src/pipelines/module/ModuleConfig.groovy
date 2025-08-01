package pipelines.module

import groovy.transform.builder.Builder
import pipelines.PipelineConfig
import pipelines.stages.RollbackModules

class ModuleConfig {
    def jenkins
    String projectName
    String projectVersion
    String vertical
    String namespace
    String metadataBucket
    String previousVersion = null
    Boolean isAutomatedDeploy = false
    Boolean isBranchDeploy = false
    Boolean skipDeploymentGates = false
    String env = null
    String region = null
    RollbackModules rollback = null
    Boolean uninstall = false
    Boolean isPrBuild = false
    Boolean isMergeQueueBuild = false

    @Builder
    ModuleConfig(def jenkins, String projectName, String projectVersion,
    String vertical, String namespace, String metadataBucket,
    Boolean isBranchDeploy = false, Boolean isAutomatedDeploy = false,
    String previousVersion = null, Boolean skipDeploymentGates = false,
    String env = null, String region = null, Boolean uninstall = false,
    PipelineConfig pipelineConfig = null) {
        this.jenkins = jenkins ?: pipelineConfig?.jenkins
        this.projectName = projectName ?: pipelineConfig?.projectName
        this.projectVersion = projectVersion
        this.vertical = vertical ?: pipelineConfig?.vertical
        this.namespace = namespace ?: pipelineConfig?.namespace
        this.metadataBucket = metadataBucket ?: pipelineConfig?.deploymentMetadataBucket
        this.isBranchDeploy = isBranchDeploy
        this.isAutomatedDeploy = isAutomatedDeploy
        this.previousVersion = previousVersion
        this.skipDeploymentGates = skipDeploymentGates
        this.env = env
        this.region = region ?: pipelineConfig?.regions?.first()
        this.uninstall = uninstall
        this.isPrBuild = pipelineConfig?.isPrBuild
        this.isMergeQueueBuild = pipelineConfig?.isMergeQueueBuild
    }

    ModuleConfig(ModuleConfig config, String region) {
        this.jenkins = config.jenkins
        this.projectName = config.projectName
        this.projectVersion = config.projectVersion
        this.vertical = config.vertical
        this.namespace = config.namespace
        this.metadataBucket = config.metadataBucket
        this.isAutomatedDeploy = config.isAutomatedDeploy
        this.isBranchDeploy = config.isBranchDeploy
        this.previousVersion = config.previousVersion
        this.skipDeploymentGates = config.skipDeploymentGates
        this.env = config.env
        this.region = region
        this.uninstall = config.uninstall
        this.isPrBuild = config.isPrBuild
        this.isMergeQueueBuild = config.isMergeQueueBuild
    }

    ModuleConfig(ModuleConfig config, RollbackModules rollback) {
        this.jenkins = config.jenkins
        this.projectName = config.projectName
        this.projectVersion = config.projectVersion
        this.vertical = config.vertical
        this.namespace = config.namespace
        this.metadataBucket = config.metadataBucket
        this.isAutomatedDeploy = config.isAutomatedDeploy
        this.isBranchDeploy = config.isBranchDeploy
        this.previousVersion = config.previousVersion
        this.skipDeploymentGates = config.skipDeploymentGates
        this.env = config.env
        this.rollback = rollback
        this.region = config.region
        this.uninstall = config.uninstall
        this.isPrBuild = config.isPrBuild
        this.isMergeQueueBuild = config.isMergeQueueBuild
    }
}
