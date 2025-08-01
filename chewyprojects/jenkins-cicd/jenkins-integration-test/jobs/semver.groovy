pipelineJob('semver-integration-test') {
    definition {
        cps {
            script(new File('/library/jenkins-integration-test/pipelines/semver.groovy').getText('UTF-8'))
        }
    }
}
