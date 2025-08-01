package pipelines.github

import static pipelines.github.Api.deleteFromGithub
import static pipelines.github.Api.fetchFromGitHub
import static pipelines.github.Api.postToGithub
import static pipelines.github.Repository.guardInput
import static pipelines.nullability.StringNullability.assertNotBlank

/**
 * This class provides methods to interact with GitHub pull requests.
 */
class PullRequest {

    static String updateComment(String owner, String repo, String token, int prNumber, String comment, String id = '') {
        guardComment(owner, repo, token, prNumber)

        deleteComment(owner, repo, token, prNumber, commentId(id))

        if (comment == null || comment.blank) {
            return null
        }

        String prefixedComment = "${hiddenText(commentId(id))}\n\n${comment}"

        return postToGithub("${owner}/${repo}/issues/${prNumber}/comments", token, prefixedComment)
    }

    static Object listComments(String owner, String repo, String token, int prNumber, String filterId = null) {
        guardComment(owner, repo, token, prNumber)

        Object comments = fetchFromGitHub("${owner}/${repo}/issues/${prNumber}/comments", token)

        if (filterId) {
            return comments.findAll { it.body.contains(filterId) }
        }

        return comments
    }

    static boolean deleteComment(String owner, String repo, String token, int prNumber, String id) {
        guardComment(owner, repo, token, prNumber)

        Object comments = listComments(owner, repo, token, prNumber, id)

        if (!comments.empty) {
            Object commentToDelete = comments[0]

            if (commentToDelete?.id) {
                return deleteFromGithub(
                        "${owner}/${repo}/issues/comments/${commentToDelete.id}",
                        token
                        )
            }
        }

        return false
    }

    static String getTitle(String owner, String repo, String token, int prNumber) {
        return getPullRequest(owner, repo, token, prNumber).title
    }

    static Object getPullRequest(String owner, String repo, String token, int prNumber) {
        guardComment(owner, repo, token, prNumber)

        return fetchFromGitHub("${owner}/${repo}/pulls/${prNumber}", token)
    }

    static String getPullRequestNumber(String owner, String repo, String token, String branchName, String base) {
        guardInput(owner, repo, token)

        Object pulls = fetchFromGitHub("${owner}/${repo}/pulls?" +
                "head=${owner}:${assertNotBlank(branchName)}&state=open&base=${assertNotBlank(base)}", token)

        if (pulls?.size() == 1) {
            return pulls[0].number
        }

        return null
    }

    private static String hiddenText(String content) {
        return """<!-- "${content}" -->"""
    }

    private static String commentId(String id) {
        return "releng-update-comment_${id}"
    }

    private static void guardComment(String owner, String repo, String token, int prNumber) {
        guardInput(owner, repo, token)
        guardPrNumber(prNumber)
    }

    private static void guardPrNumber(int prNumber) {
        // groovylint-disable-next-line DuplicateNumberLiteral
        assert prNumber > 0: 'Pull request number must be positive'
    }
}
