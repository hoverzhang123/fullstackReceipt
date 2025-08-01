package pipelines.post


class GlobalPost {
    def postParams
    def jenkins
    String projectName
    boolean isBranchPipeline
    boolean isMergeQueue

    String notificationColor = "green"

    GlobalPost(def postParams, def jenkins, String projectName, boolean isBranchPipeline = false, boolean isMergeQueue = false) {
        this.postParams = postParams
        this.jenkins = jenkins
        this.projectName = projectName
        this.isBranchPipeline = isBranchPipeline
        this.isMergeQueue = isMergeQueue
    }

    void failure() {
        if (postParams != null && postParams.failure != null) {
            postParams.failure()
        }
        if (!isBranchPipeline) {
            jenkins.notifyFailure(projectName, "${jenkins.env.DEPLOY_STEP}")
        }
        if (isMergeQueue) {
            // Notify github that the merge queue build status failed
            jenkins.notifyPRMergeStatusForMergeQueue(buildSucceeded: false, repositoryName: projectName)
        }
        if (jenkins.env.ENVIRONMENT == "prd" && jenkins.env.CR_NUMBER) {
            jenkins.notifyFailure(projectName, "prd release is failed, check CR" +
                    " (<https://chewy.service-now.com/task.do?sysparm_query=number=${jenkins.env.CR_NUMBER}|${jenkins.env.CR_NUMBER}>)")
        }
    }

    void success(String message = null, boolean enabled = false, boolean priorityNotification = false) {
        if (postParams != null && postParams.success != null) {
            postParams.success()
        }
        if (message != null && enabled) {
            jenkins.notify(projectName, message, notificationColor, false, priorityNotification)
        }
        if (isMergeQueue) {
            // Notify github that the merge queue build status succeeded
            jenkins.notifyPRMergeStatusForMergeQueue(buildSucceeded: true, repositoryName: projectName)
        }
    }

    void always() {
        if (postParams != null && postParams.always != null) {
            postParams.always()
        }
    }
}
