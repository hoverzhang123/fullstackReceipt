package pipelines

import pipelines.module.Module
import pipelines.module.LambdaModule
import pipelines.module.TerraformModule
import pipelines.stages.BuildModules
import pipelines.stages.DeployModules
import pipelines.stages.PublishModules
import pipelines.stages.ValidateModules

class TerraformBranchPipeline {
    PipelineConfig pipelineConfig
    String projectVersion
    def jenkins
    String projectName
    List<Module> modules

    TerraformBranchPipeline(PipelineConfig pipelineConfig, String projectVersion) {
        this.pipelineConfig = pipelineConfig
        this.projectVersion = projectVersion
        this.jenkins = pipelineConfig.jenkins
        this.projectName = pipelineConfig.projectName
        this.modules = pipelineConfig.modules.findAll {it instanceof TerraformModule || it instanceof LambdaModule}
    }

    void run() {
        // build
        new BuildModules(pipelineConfig, this.projectVersion).run()
        // validate
        new ValidateModules(pipelineConfig, this.projectVersion, true).run()
        // publish
        new PublishModules(pipelineConfig, projectName, this.projectVersion, null, modules).run(
                isBranchDeploy: true
                )

        // deploy branch build - only deploy us-east-1
        new DeployModules(pipelineConfig, this.projectVersion, modules, 'dev').run([
            isBranchDeploy: true,
            skipDeploymentGates: true,
        ])
    }
}
