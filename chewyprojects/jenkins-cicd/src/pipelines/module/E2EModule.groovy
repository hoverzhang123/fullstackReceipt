package pipelines.module

import pipelines.PipelineConfig


class E2EModule extends Module {
    List<String> e2eTestEnvironments
    E2EModule(String name, List<String> e2eTestEnvironments) {
        super([name: name, type: 'e2e'])
        this.e2eTestEnvironments = e2eTestEnvironments
    }

    E2EModule(PipelineConfig pipeline, props) {
        super(props)
        this.e2eTestEnvironments = props.testEnvironments ?: pipeline.deploymentLowerEnvironments.flatten() as List<String>
    }

    Boolean shouldRunE2ETests(ModuleConfig config) {
        !config.skipDeploymentGates && e2eTestEnvironments.contains(config.env)
    }

    void runE2Etests(ModuleConfig config) {
        def jenkins = config.jenkins
        String env = config.env

        // TODO: E2E failures cause the entire pipeline to stop, this does not give us the ability to rollback which we
        // may want to do in the staging case. Change this to return the e2e status as a boolean rather than outright fail
        jenkins.env.DEPLOY_STEP = "Run e2e tests ${env}"
        def commitHash = jenkins.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
        def authorName = jenkins.sh(returnStdout: true, script: 'git log -1 --pretty=%an').trim().toString()
        // TODO: allow other e2e test jobs to be run
        if (name == 'global') {
            // DEFAULT global e2e test job for rxp e2e tests
            // Special keyword to run the global rxp e2e tests
            jenkins.build job: 'deploy-rxp-e2e-tests', parameters: [
                jenkins.string(name: 'environment', value: env),
                jenkins.string(name: 'commit_hash', value: commitHash),
                jenkins.string(name: 'author_name', value: authorName)
            ],
            wait: true
        } else {
            // TODO: Revisit this
            // Run with AWS so tests can get secrets as needed
            jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS[env]}:role/CHEWY-cross-jenkins") {
                jenkins.sh "./gradlew ${this.name}:test -Denv=${env} --rerun-tasks"
            }
        }
    }

    @Override
    protected void initializeStages() {
        postCheck = new Stage(this.&runE2Etests, this.&shouldRunE2ETests)
    }
}
