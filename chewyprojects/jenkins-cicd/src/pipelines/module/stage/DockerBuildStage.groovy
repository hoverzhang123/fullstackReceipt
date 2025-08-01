package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.metadata.GradleCommandUtil

class DockerBuildStage extends AbstractStage {
    String moduleName

    DockerBuildStage(String moduleName) {
        this.moduleName = moduleName
    }

    @Override
    void initializeStage() {
        runner = this.&build
    }

    void build(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        def stageName = moduleName ?: config.projectName
        jenkins.env.DEPLOY_STEP = "Build ${stageName}(${moduleName}) - ${projectVersion}"
        GradleCommandUtil.executeGradleAction(config, moduleName, "dockerBuildImage -x check")
    }
}
