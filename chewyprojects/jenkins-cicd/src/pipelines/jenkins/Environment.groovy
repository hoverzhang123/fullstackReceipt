package pipelines.jenkins

class Environment {
    private static final String BRANCH_NAME = 'BRANCH_NAME'
    private static final String CHANGE_BRANCH = 'CHANGE_BRANCH'
    private static final String CHANGE_URL = 'CHANGE_URL'
    private static final String GH_TOKEN = 'GH_TOKEN'
    private static final String GIT_BRANCH = 'GIT_BRANCH'
    private static final String GIT_URL = 'GIT_URL'

    /**
     * Get the Git repository URL from the Jenkins environment
     * @param env The Jenkins environment object
     * @return The Git URL or empty string if not found
     */
    static String getGitUrl(Object env) {
        return getValue(env, GIT_URL)
    }

    /**
     * Get the change/pull request URL from the Jenkins environment
     * @param env The Jenkins environment object
     * @return The change URL or empty string if not found
     */
    static String getChangeUrl(Object env) {
        return getValue(env, CHANGE_URL)
    }

    /**
     * Get the Git branch from the Jenkins environment
     * @param env The Jenkins environment object
     * @return The Git branch or empty string if not found
     */
    static String getGitBranch(Object env) {
        return getValue(env, GIT_BRANCH)
    }

    /**
     * Get the branch name from the Jenkins environment
     * @param env The Jenkins environment object
     * @return The branch name or empty string if not found
     */
    static String getBranchName(Object env) {
        return getValue(env, BRANCH_NAME)
    }

    /**
     * Get the change/pull request branch from the Jenkins environment
     * @param env The Jenkins environment object
     * @return The change branch or empty string if not found
     */
    static String getChangeBranch(Object env) {
        return getValue(env, CHANGE_BRANCH)
    }

    /**
     * Get the GitHub authentication token from the Jenkins environment
     * @param env The Jenkins environment object
     * @return The GitHub token or empty string if not found
     */
    static String getGithubToken(Object env) {
        return getValue(env, GH_TOKEN)
    }

    /**
     * Get a value from the Jenkins environment
     * @param env The Jenkins environment object
     * @param key The environment variable key to look up
     * @param defaultValue The default value to return if the key is not found
     * @return The value from the environment or the default value if not found
     */
    static String getValue(Object env, String key, String defaultValue = '') {
        return getValue(env, [key] as String[], defaultValue)
    }

    /**
     * Get a value from the Jenkins environment, trying multiple keys in order
     * @param env The Jenkins environment object
     * @param keys Array of environment variable keys to try
     * @param defaultValue The default value to return if none of the keys are found
     * @return The first value found in the environment or the default value if none found
     */
    static String getValue(Object env, String[] keys, String defaultValue = '') {
        for (String key : keys) {
            if (env[key] != null) {
                return env[key]
            }
        }

        return defaultValue
    }
}
