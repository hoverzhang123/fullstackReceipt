package pipelines.module.abstracttool

import pipelines.module.ModuleConfig
import pipelines.metadata.BuildEnvironmentUtil
import pipelines.module.CommandContext

/**
 * Build command factory class
 * Creates appropriate build command implementations based on configuration
 */
class BuildCommandFactory {

    /**
     * Create build command
     * @param config Module configuration containing jenkins context and project info
     * @param props Module properties, including the buildEnvironment configuration
     * @return BuildCommand instance
     */
    static Command createBuildCommand(ModuleConfig config, CommandContext commandContext) {
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

        jenkins.echo("Build tool strategy: ${tool}")

        switch (tool) {
            case 'gradle':
                jenkins.echo("Using standard Gradle build strategy")
                return new GradleBuildCommand(commandContext)

            case 'gradledocker':
                jenkins.echo("Using Gradle Docker build strategy")
                return new DockerxBuildCommand(commandContext)

            case 'python':
                jenkins.echo("Using Python build strategy")
                return new GradleBuildCommand(commandContext)

            default:
                jenkins.echo("Using default build strategy for tool: GradleBuildCommand")
                return new GradleBuildCommand(commandContext)
        }
    }
}
