package pipelines

import groovy.transform.builder.Builder

class GatlingModuleConfig {
    String name
    String simulationId
    String slackChannel
    Boolean disableSlackNotifications
    Boolean autoRollbackOnFailure
    Boolean runInBranchBuild
    Boolean runInMainBuild
    Boolean runInMergeQueueBuild
    Boolean runInPrBuild
    String failureStatus

    @Builder
    GatlingModuleConfig(String name,
    String simulationId,
    String slackChannel,
    Boolean disableSlackNotifications,
    Boolean autoRollbackOnFailure,
    Boolean runInBranchBuild,
    Boolean runInMainBuild,
    Boolean runInMergeQueueBuild,
    Boolean runInPrBuild,
    String failureStatus) {
        this.name = name ?: "gatling ${simulationId}"
        this.simulationId = simulationId
        this.slackChannel = slackChannel
        this.disableSlackNotifications = disableSlackNotifications == null ? false : disableSlackNotifications
        this.autoRollbackOnFailure = autoRollbackOnFailure == null ? false : autoRollbackOnFailure
        this.runInBranchBuild = runInBranchBuild == null ? false : runInBranchBuild
        this.runInMainBuild = runInMainBuild == null ? true : runInMainBuild
        this.runInMergeQueueBuild = runInMergeQueueBuild == null ? false : runInMergeQueueBuild
        this.runInPrBuild = runInPrBuild == null ? false : runInPrBuild
        this.failureStatus = failureStatus ?: 'FAIL'
    }

    GatlingModuleConfig(PipelineConfig config, def props) {
        this.name = props.name = props.name ?: "gatling ${props.simulationId}"
        this.simulationId = props.simulationId
        this.slackChannel = props.slackChannel ?: config.slackParams.slackChannel
        this.disableSlackNotifications = props.disableSlackNotifications == null ? false : props.disableSlackNotifications
        this.autoRollbackOnFailure = props.autoRollbackOnFailure == null ? false : props.autoRollbackOnFailure
        this.runInBranchBuild = props.runInBranchBuild == null ? false : props.runInBranchBuild
        this.runInMainBuild = props.runInMainBuild == null ? true : props.runInMainBuild
        this.runInMergeQueueBuild = props.runInMergeQueueBuild == null ? false : props.runInMergeQueueBuild
        this.runInPrBuild = props.runInPrBuild == null ? false : props.runInPrBuild
        this.failureStatus = props.failureStatus ?: 'FAIL'
    }
}
