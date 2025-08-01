package pipelines

import groovy.transform.builder.Builder
import pipelines.module.Module

class PipelineConfig {
    public static final String CHOOSE_ALL_SELECT_OPTION = 'ALL'
    public static final String DEPLOY = 'deploy'
    public static final String LATEST_TAG = 'Latest'
    public static final String ROLLBACK = 'rollback'
    public static final String PET_HEALTH_NAMESPACE = 'pet-health'
    public static final List<String> SKIP_CI_STRINGS = [
        '[skip-ci]',
        '[ci-skip]',
        '[no ci]',
        '[skip actions]',
        '[actions skip]',
    ]

    def jenkins
    String projectName
    List<String> regions
    List<Module> modules
    String assignmentGroup
    String vertical
    String clusterName
    String namespace
    String deploymentMetadataBucket
    List<String> mergeQueueDeploymentEnvs
    List<String> featureBranchDeploymentEnvs
    List<String> skipDeploymentGatesLowerEnvs
    List<List<String>> deploymentLowerEnvironments
    Boolean respectChangeFreezesProd
    Boolean respectChangeFreezesNonprod
    Map postParams
    String branchName
    String environment
    Boolean rollback
    Boolean isPrBuild
    Boolean isMergeQueueBuild
    Boolean enableFeatureBranchBuilds
    Boolean isLowerEnvironment
    Map slackParams
    String versionIncrement

    @Builder
    PipelineConfig(def jenkins,
    String projectName,
    List<String> regions,
    String assignmentGroup,
    List<Module> modules,
    String vertical,
    String clusterName,
    String namespace,
    String deploymentMetadataBucket,
    List<String> mergeQueueDeploymentEnvs,
    List<String> featureBranchDeploymentEnvs,
    List<List<String>> deploymentLowerEnvironments,
    List<String> skipDeploymentGatesLowerEnvs,
    Boolean respectChangeFreezesProd,
    Boolean respectChangeFreezesNonprod,
    Boolean enableFeatureBranchBuilds,
    Map postParams,
    Map slackParams,
    String versionIncrement
    ) {
        this.jenkins = jenkins
        this.projectName = projectName
        this.regions = regions ?: ['us-east-1', 'us-east-2']
        this.assignmentGroup = assignmentGroup
        this.modules = modules
        this.vertical = vertical ?: 'hlth'
        this.clusterName = clusterName ?: 'rxp-eks-cluster'
        this.namespace = namespace ?: PET_HEALTH_NAMESPACE
        this.deploymentMetadataBucket = deploymentMetadataBucket ?: 'shd-use1-jenkins-cicd-deployment-metadata-bucket'
        this.mergeQueueDeploymentEnvs = mergeQueueDeploymentEnvs ?: ['dev']
        this.featureBranchDeploymentEnvs = featureBranchDeploymentEnvs ?: ['dev']
        // skip deployment gates in truck merge dev env by default
        this.skipDeploymentGatesLowerEnvs = skipDeploymentGatesLowerEnvs ?: ['dev']
        this.deploymentLowerEnvironments = deploymentLowerEnvironments ?: [['dev'], ['qat'], ['stg']]
        this.respectChangeFreezesProd = respectChangeFreezesProd != null ? respectChangeFreezesProd : true
        this.respectChangeFreezesNonprod = respectChangeFreezesNonprod != null ? respectChangeFreezesProd : true
        this.postParams = postParams ?: null
        this.isPrBuild = jenkins.env.CHANGE_ID == null ? false : jenkins.env.CHANGE_ID
        this.enableFeatureBranchBuilds = enableFeatureBranchBuilds == null ? false : enableFeatureBranchBuilds
        this.branchName = isPrBuild ? jenkins.env.CHANGE_BRANCH : jenkins.env.BRANCH_NAME
        this.environment = jenkins.env.ENVIRONMENT ?: jenkins.params.ENVIRONMENT
        this.rollback = jenkins.params && jenkins.params.ACTION == ROLLBACK
        this.isLowerEnvironment = this.deploymentLowerEnvironments.flatten().contains(environment)
        this.slackParams = slackParams ?: [ failureOnly: false, channel: 'rxp-notifications', prodChannel: 'rxp-notifications' ]
        this.versionIncrement = versionIncrement ?: 'minor'
    }

    List<String> getBranchDeploymentEnvs(boolean isMergeQueue) {
        if (hasEnvironmentOverride()) {
            return Arrays.asList(jenkins.params.ENVIRONMENT)
        }

        return isMergeQueue ? mergeQueueDeploymentEnvs : featureBranchDeploymentEnvs
    }

    List<List<String>> getPreProdDeploymentEnvs() {
        if (hasEnvironmentOverride()) {
            return [
                Arrays.asList(jenkins.params.ENVIRONMENT)
            ]
        }

        return deploymentLowerEnvironments
    }

    Boolean doBranchBuild() {
        return isPrBuild || enableFeatureBranchBuilds
    }

    boolean sendLowerEnvironmentSuccess() {
        return !slackParams?.failureOnly
    }

    Boolean hasEnvironmentOverride() {
        return jenkins.params.ENVIRONMENT &&
                jenkins.params.ENVIRONMENT != CHOOSE_ALL_SELECT_OPTION &&
                isLowerEnvironment
    }
}
