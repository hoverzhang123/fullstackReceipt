package pipelines.metadata

/**
 * Build environment utility class
 * Shared utilities for processing build environment configuration
 */
class BuildEnvironmentUtil {

    /**
     * Determines if the build environment contains a valid Dockerfilepath action configuration
     * @param buildEnvironment Build environment configuration (pre-validated, not null)
     * @return true if actions contains a Map with a non-empty dockerfilepath value, false otherwise
     */
    static boolean hasDockerfileAction(Map<String, Object> buildEnvironment) {
        // Extract the actions from the build environment
        def actions = buildEnvironment.actions

        // Validate actions is a Map object
        if (!(actions instanceof Map)) {
            return false
        }

        // Iterate through each action configuration to find a valid dockerfilepath
        for (def actionConfig in ((Map) actions).values()) {
            // Ensure the action's configuration is a Map
            if (actionConfig instanceof Map) {
                // Verify the dockerfilepath key exists in the action's configuration Map
                if (((Map) actionConfig).containsKey('dockerfilepath')) {
                    // Ensure the dockerfilepath value is not null and not an empty string
                    def dockerfilePath = ((Map) actionConfig).get('dockerfilepath')
                    if (dockerfilePath != null && !dockerfilePath.toString().trim().isEmpty()) {
                        // Found a valid Dockerfile action, so we can return true
                        return true
                    }
                }
            }
        }

        // No valid Dockerfile action was found after checking all actions
        return false
    }

    /**
     * Determine the effective tool strategy based on buildEnvironment
     * @param buildEnvironment Build environment configuration (pre-validated by BuildCommandFactory)
     * @return effective tool strategy ('gradle', 'gradledocker', 'python', etc.)
     */
    static String getEffectiveTool(Map<String, Object> buildEnvironment) {
        String tool = buildEnvironment.tool.toString().toLowerCase()

        // If gradle + dockerfile action, use gradledocker strategy
        if (tool == 'gradle' && hasDockerfileAction(buildEnvironment)) {
            return 'gradledocker'
        }

        return tool
    }
}
