package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.module.CommandContext
import pipelines.module.abstracttool.Command
import pipelines.module.abstracttool.BuildCommandFactory

class BuildEnvironmentStage extends AbstractStage {
    String action
    BuildEnvironmentStage(String action) {
        this.action = action
    }

    @Override
    void initializeStage() {
        runner = this.&build
    }

    void build(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        def moduleName = this.moduleProps.name
        def stageName = moduleName ?: config.projectName
        def commandContext = new CommandContext(action: this.action, props: this.moduleProps)

        jenkins.env.DEPLOY_STEP = "${this.action} ${stageName}(${moduleName}) - ${projectVersion}"
        Command buildCommand = BuildCommandFactory.createBuildCommand(config, commandContext)
        jenkins.echo("Using build strategy: ${buildCommand.getDescription()}")
        buildCommand.execute(config, moduleName)
    }
}
