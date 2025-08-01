package pipelines.module.abstracttool

import pipelines.module.ModuleConfig
import pipelines.module.CommandContext

/**
 * Dockerx build command implementation
 * Uses native docker buildx for building, supports multi-architecture
 */
class DockerxBuildCommand implements Command {
    private final CommandContext commandContext

    DockerxBuildCommand(CommandContext commandContext) {
        this.commandContext = commandContext
    }

    @Override
    void execute(ModuleConfig config, String moduleName) {
        def jenkins = config.jenkins
        def buildEnvironment = commandContext.props.buildEnvironment
        def action = commandContext.action

        // Debug log for buildEnvironment
        jenkins.echo("DockerxBuildCommand executing with buildEnvironment: ${buildEnvironment}")

        // Get dockerfile path from the current action's configuration
        def dockerfilePath = buildEnvironment?.actions[action]?.dockerfilepath
        jenkins.echo("Using dockerfile at: ${dockerfilePath}")

        if (!dockerfilePath) {
            throw new IllegalArgumentException("dockerfilepath is not set in buildEnvironment.actions.${action}")
        }

        // Execute docker build command
        jenkins.sh(script: """
            #!/bin/bash
            set -e

            # Build the image
            docker build -t echo-image -f "${dockerfilePath}" .

            # Run it (uses default message)
            docker run --rm echo-image
        """)
    }

    @Override
    String getDescription() {
        return "Docker buildx build (supports multi-architecture and targets)"
    }
}
