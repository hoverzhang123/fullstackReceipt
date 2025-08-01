package pipelines.module.abstracttool

import pipelines.module.ModuleConfig
import pipelines.module.stage.SonarqubeStage
import pipelines.module.CommandContext

/**
 * Gradle validate command implementation
 * Uses gradle's check and sonar tasks for validation
 */
class GradleValidateCommand implements Command {
    private final CommandContext commandContext

    GradleValidateCommand(CommandContext commandContext) {
        this.commandContext = commandContext
    }

    @Override
    void execute(ModuleConfig config, String moduleName) {
        def jenkins = config.jenkins
        def buildEnvironment = commandContext.props.buildEnvironment

        jenkins.echo("GradleValidateCommand executing with buildEnvironment: ${buildEnvironment}")

        SonarqubeStage.checkSonarQube(config, moduleName)

        // Collect test results
        String testResults = jenkins.sh(returnStdout: true, script: "find . | grep test-results || true | grep xml || true").trim()
        if (testResults) {
            jenkins.junit '**/test-results/**/*.xml'
        }
    }

    @Override
    String getDescription() {
        return "Gradle Validate with check and sonar tasks"
    }
}
