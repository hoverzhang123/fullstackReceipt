package pipelines.stages

import pipelines.PipelineConfig
import pipelines.module.ModuleConfig

class RollbackModules {
    PipelineConfig pipelineConfig
    def jenkins
    String env
    List<String> regions
    String rollbackVersion

    RollbackModules(PipelineConfig pipelineConfig, String rollbackVersion, String env, List<String> regions) {
        this.pipelineConfig = pipelineConfig
        this.jenkins = pipelineConfig.jenkins
        this.rollbackVersion = rollbackVersion
        this.env = env
        this.regions = regions
    }

    void run() {
        ModuleConfig moduleConfig = ModuleConfig.builder()
                .pipelineConfig(pipelineConfig)
                .previousVersion(rollbackVersion)
                .env(env)
                .build()

        jenkins.echo("Rollback modules: ${pipelineConfig.modules}")

        def stages = [:]

        pipelineConfig.modules.each({
            it.globalRollback.addStage(stages, moduleConfig, "Global Rollback", it.name)
        })

        regions.each { region ->
            def regionConfig = new ModuleConfig(moduleConfig, region)
            pipelineConfig.modules.each({
                it.regionRollback.addStage(stages, regionConfig, "Regional Rollback ${region}", it.name)
            })
        }

        jenkins.stage("Rollback modules ${env}") {
            jenkins.parallel stages
        }
    }
}
