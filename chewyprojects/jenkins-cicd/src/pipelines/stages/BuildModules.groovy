package pipelines.stages

import pipelines.PipelineConfig
import pipelines.module.ModuleConfig

class BuildModules {
    PipelineConfig pipelineConfig
    def jenkins
    String projectVersion
    Boolean isBranchDeploy

    BuildModules(PipelineConfig pipelineConfig, String projectVersion, Boolean isBranchDeploy = false) {
        this.pipelineConfig = pipelineConfig
        this.jenkins = pipelineConfig.jenkins
        this.projectVersion = projectVersion
        this.isBranchDeploy = isBranchDeploy
    }

    void run() {
        def stages = [:]

        jenkins.echo("Building modules: ${pipelineConfig.modules}")
        ModuleConfig config = ModuleConfig.builder()
                .pipelineConfig(pipelineConfig)
                .projectVersion(projectVersion)
                .vertical(pipelineConfig.vertical)
                .namespace(pipelineConfig.namespace)
                .isBranchDeploy(isBranchDeploy)
                .build()

        pipelineConfig.modules.each {it.build.addStage(stages, config, "Build", it.name)}

        jenkins.stage("Build modules") {
            jenkins.parallel stages
        }
    }
}
