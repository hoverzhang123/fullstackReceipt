package pipelines.module.abstracttool

import pipelines.module.ModuleConfig
import pipelines.metadata.GradleCommandUtil
import pipelines.module.CommandContext


class GradleBuildCommand implements Command {
    private final CommandContext commandContext

    /**
     * @param commandContext A map containing the action and the module properties.
     */
    GradleBuildCommand(CommandContext commandContext) {
        this.commandContext = commandContext
    }

    @Override
    void execute(ModuleConfig config, String moduleName) {
        def jenkins = config.jenkins

        jenkins.echo("Executing in 'actions' mode.")
        Map actions = commandContext.props.buildEnvironment?.actions
        jenkins.echo("actions: ${actions}")
        String commandAction = commandContext.action.toLowerCase()
        String task = actions?.get(commandAction)?.task
        jenkins.echo("task: ${task}")

        if (!task) {
            jenkins.echo("Executing in 'actions fallback' mode.")
            def defaultTask = getDefaultTask(commandAction)
            def projectVersion = config.projectVersion
            jenkins.echo("No specific action config found, falling back to default task: '${defaultTask}'")
            jenkins.sh "./gradlew -PprojectVersion=${projectVersion} ${defaultTask}"
            return
        }
        jenkins.echo("Executing task from actions: '${task}'")
        GradleCommandUtil.executeGradleAction(config, moduleName, task)
    }

    @Override
    String getDescription() {
        return "Gradle command executor name is ${commandContext.props.name} and action: '${commandContext.action.toLowerCase()}.'"
    }

    private String getDefaultTask(String action) {
        def moduleName = commandContext.props.name
        switch(action.toLowerCase()) {
            case 'build':
                return "${moduleName}:build"
            case 'publish':
                return "${moduleName}:artifactoryPublish -x check"
        }
    }
}
