package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.metadata.GradleCommandUtil

class SonarqubeStage extends AbstractStage {
    String moduleName

    SonarqubeStage(String moduleName) {
        this.moduleName = moduleName
    }

    static void checkSonarQube(ModuleConfig config, String moduleName) {
        def jenkins = config.jenkins
        jenkins.steps.lock(resource: "sonarqubeStage: ${moduleName ?: "root"}") {
            jenkins.withSonarQubeEnv('sonarqube-nonprod') {
                GradleCommandUtil.executeGradleCheckSonar(config, moduleName)
            }
        }
    }

    @Override
    void initializeStage() {
        runner = this.&runSonarqube
    }

    void runSonarqube(ModuleConfig config) {
        def jenkins = config.jenkins
        def stageName = moduleName ?: config.projectName
        jenkins.env.DEPLOY_STEP = "${stageName} Unit Tests"

        checkSonarQube(config, moduleName)
        String testResults = jenkins.sh(returnStdout: true, script: "find . | grep test-results || true | grep xml || true").trim()
        if (testResults) {
            jenkins.junit '**/test-results/**/*.xml'
        }
    }
}
