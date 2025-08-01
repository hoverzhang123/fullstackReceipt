package pipelines.github

import static pipelines.jenkins.Environment.getChangeUrl
import static pipelines.jenkins.Environment.getGitUrl
import static pipelines.jenkins.Environment.getValue
import static pipelines.github.Auth.withGithubAuth
import static pipelines.github.Repository.getDefaultBranch
import static pipelines.github.PullRequest.getPullRequestNumber
import static pipelines.github.PullRequest.updateComment as updateCommentImpl

/**
 * This class provides methods to interact with GitHub repositories.
 */
class Github {

    private static final String GITHUB_TOKEN_VAR = 'GH_TOKEN'

    static String token(Object env) {
        return getValue(env, GITHUB_TOKEN_VAR)
    }

    /**
     * Updates a comment on a GitHub pull request, deleting the previous comment if empty
     *
     * @param script Jenkins script context
     * @param comment content to be posted
     * @param baseBranch base branch name (optional)
     * @param prNumber The number of the pull request; always a positive number
     * @param id unique id for the comment to be updated or deleted
     * @param githubOwner The owner of the repository (e.g., Chewy-Inc | Chewy-Int)
     * @param githubRepo The name of the repository
     *
     * @return null on success
     * @throws Exception If the API request fails
     */
    static Object updateComment(
            Object script,
            String comment = '',
            String baseBranch = null,
            String prNumber = '0',
            String id = null,
            String githubOwner = null,
            String githubRepo = null
    ) {
        String owner = githubOwner ?: Repository.getOwner([
            getGitUrl(script.env),
            getChangeUrl(script.env),
        ])
        String repo = githubRepo ?: Repository.getRepository([
            getGitUrl(script.env),
            getChangeUrl(script.env),
        ])

        return withGithubAuth(script) {
            int number = prNumberFallback(script.env, prNumber)

            if (number <= 0) {
                [
                    script.env.BRANCH_NAME,
                    script.env.GIT_BRANCH,
                    script.env.CHANGE_BRANCH,
                ].find { branch ->
                    if (branch == null) {
                        return false
                    }

                    try {
                        String numberFromBranch = getPullRequestNumber(
                                owner,
                                repo,
                                token(script.env),
                                branch,
                                baseBranch ?: getDefaultBranch(owner, repo, token(script.env))
                                )

                        if (numberFromBranch?.isInteger()) {
                            number = numberFromBranch.toInteger()
                            return true
                        }
                    } catch (IOException | IllegalArgumentException e) {
                        script.echo("Failed to get PR number for branch ${branch}: ${e.message}")
                    }

                    return false
                }
            }

            if (number <= 0) {
                script.echo("Invalid PR number: ${prNumber}, no comment will be posted")
                return null
            }

            return updateCommentImpl(owner, repo, token(script.env), number, comment, numberToId(id, number))
        }
    }

    private static int prNumberFallback(Object env, String prNumber) {
        // Safely parse prNumber once
        Integer parsedPrNumber = null
        if (prNumber?.isInteger()) {
            parsedPrNumber = prNumber.toInteger()
        }

        // Return prNumber if it's a positive integer
        if (parsedPrNumber != null && parsedPrNumber > 0) {
            return parsedPrNumber
        }

        // Fall back to env.CHANGE_ID if available and valid
        if (env.CHANGE_ID != null) {
            String changeIdStr = env.CHANGE_ID
            if (changeIdStr?.isInteger()) {
                Integer changeId = changeIdStr.toInteger()
                if (changeId > 0) {
                    return changeId
                }
            }
        }

        // Default fallback value
        return 0
    }

    private static String numberToId(String id, int fallback) {
        return id == null ? fallback.toString() : id
    }

}
