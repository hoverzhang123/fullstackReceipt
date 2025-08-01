package pipelines

import groovy.transform.builder.Builder

class PlaywrightModuleConfig {
    String name
    List<String> testSuites
    String testTargetName
    List<String> secretKeys
    Map<String,String> envMap
    String playwrightImage
    Boolean autoRollbackOnFailure
    Boolean runInPRBuild
    String prdFailureStatus
    String nonPrdFailureStatus

    @Builder
    PlaywrightModuleConfig(String name,
    List<String> testSuites,
    String testTargetName,
    List<String> secretKeys,
    Map<String,String> envMap,
    String playwrightImage,
    Boolean autoRollbackOnFailure,
    Boolean runInPRBuild,
    String prdFailureStatus,
    String nonPrdFailureStatus) {
        this.name = name ?: ""
        this.testSuites = testSuites ?: []
        this.testTargetName = testTargetName ?: "playwrightTest"
        this.secretKeys = secretKeys ?: []
        this.envMap = envMap ?: [:]
        this.playwrightImage = playwrightImage ?:
                "hlth/hlth-docker-jenkins-agents:ub2204-playwright148-jre17-latest"
        this.autoRollbackOnFailure = autoRollbackOnFailure == null ? false : autoRollbackOnFailure
        this.runInPRBuild = runInPRBuild == null ? false : runInPRBuild
        this.prdFailureStatus = prdFailureStatus ?: "ERROR"
        this.nonPrdFailureStatus = nonPrdFailureStatus ?: "ERROR"
    }
}
