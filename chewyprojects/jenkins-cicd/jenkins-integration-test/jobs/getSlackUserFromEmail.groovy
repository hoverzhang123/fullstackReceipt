pipelineJob('getSlackUserFromEmail-integration-test') {
    definition {
        cps {
            script(new File('/library/jenkins-integration-test/pipelines/getSlackUserFromEmail.groovy').getText('UTF-8'))
        }
    }
}
