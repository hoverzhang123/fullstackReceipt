package pipelines.module.abstracttool

import pipelines.module.ModuleConfig
import pipelines.metadata.BuildEnvironmentUtil
import pipelines.module.CommandContext

/**
 * Validate command factory class
 * Creates appropriate validate command implementations based on configuration
 *
 * Note: This factory assumes buildEnvironment has already been validated by BuildCommandFactory
 * since ValidateEnvironmentStage runs after BuildEnvironmentStage in the pipeline execution order.
 */
class ValidateCommandFactory{

    /**
     * Create validate command
     * @param config Module configuration containing jenkins context and project info
     * @param buildEnvironment Build environment configuration (pre-validated by BuildCommandFactory)
     * @return ValidateCommand instance
     */
    static Command createValidateCommand(ModuleConfig config, CommandContext commandContext) {
        def jenkins = config.jenkins

        def buildEnvironment = commandContext.props.buildEnvironment
        // Handle null buildEnvironment - provide default behavior
        if (buildEnvironment == null) {
            jenkins.echo("Error: buildEnvironment is required but not provided for project: ${config.projectName}")
            throw new IllegalArgumentException("buildEnvironment is required but not provided for project: ${config.projectName}. Please specify environment (e.g., buildenvironment: [tool: 'gradle'])")
        }

        // Check if tool exists - if not, fail the build
        if (!buildEnvironment.tool) {
            jenkins.echo("ERROR: buildEnvironment.tool is required but not provided for project: ${config.projectName}")
            throw new IllegalArgumentException("buildEnvironment.tool is required but not provided for project: ${config.projectName}. Please specify a build tool (e.g., tool: 'gradle')")
        }

        String tool = BuildEnvironmentUtil.getEffectiveTool(buildEnvironment)

        config.jenkins.echo("Validate tool strategy: ${tool}")

        switch (tool) {
            case 'gradle':
                config.jenkins.echo("Using standard Gradle validation strategy")
                return new GradleValidateCommand(commandContext)

            case 'gradledocker':
                config.jenkins.echo("Using Gradle Docker validation strategy")
                return new DockerxValidateCommand(commandContext)

            case 'python':
                config.jenkins.echo("Using Python validation strategy (gradle-based)")
                return new GradleValidateCommand(commandContext)

            default:
                config.jenkins.echo("Using default validation strategy for tool: ${tool} -> GradleValidateCommand")
                return new GradleValidateCommand(commandContext)
        }
    }


}
