package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.module.CommandContext
import pipelines.module.abstracttool.Command
import pipelines.module.abstracttool.ValidateCommandFactory

class ValidateEnvironmentStage extends AbstractStage {
    String action

    ValidateEnvironmentStage(String action) {
        this.action = action
    }

    @Override
    void initializeStage() {
        runner = this.&validate
    }

    void validate(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        def moduleName = this.moduleProps.name
        def stageName = moduleName ?: config.projectName
        jenkins.env.DEPLOY_STEP = "Validate ${stageName}(${moduleName}) - ${projectVersion}"
        def commandContext = new CommandContext(action: this.action, props: this.moduleProps)

        Command ValidateCommand = ValidateCommandFactory.createValidateCommand(config, commandContext)
        jenkins.echo("Using validate strategy: ${ValidateCommand.getDescription()}")
        ValidateCommand.execute(config, moduleName)
    }
}
