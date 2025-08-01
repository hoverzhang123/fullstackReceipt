package pipelines.stages

import pipelines.PipelineConfig
import pipelines.module.ModuleConfig

class ValidateModules {
    PipelineConfig pipelineConfig
    def jenkins
    String projectVersion
    boolean isBranchDeploy

    ValidateModules(PipelineConfig pipelineConfig, String projectVersion, boolean isBranchDeploy) {
        this.pipelineConfig = pipelineConfig
        this.jenkins = pipelineConfig.jenkins
        this.projectVersion = projectVersion
        this.isBranchDeploy = isBranchDeploy
    }

    void run() {
        getGithubToken()

        def stages = [:]

        jenkins.echo("Validating modules: ${pipelineConfig.modules}")
        ModuleConfig config = ModuleConfig.builder()
                .pipelineConfig(pipelineConfig)
                .projectVersion(projectVersion)
                .isBranchDeploy(isBranchDeploy)
                .build()

        pipelineConfig.modules.each { it.validate.addStage(stages, config, "Validate", it.name)}

        jenkins.stage("Validate modules") {
            jenkins.parallel stages
        }
    }

    void getGithubToken() {
        jenkins.withCredentials([
            jenkins.usernamePassword(credentialsId: 'jenkins-github-userpass', passwordVariable: 'GRGIT_PASS', usernameVariable: 'GRGIT_USER')
        ]) {
            jenkins.env.GITHUB_TOKEN = jenkins.env.GRGIT_PASS
        }
    }
}
