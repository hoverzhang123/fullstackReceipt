package pipelines.github

import static pipelines.github.PullRequest.updateComment
import static pipelines.github.PullRequest.listComments
import static pipelines.github.PullRequest.deleteComment
import static pipelines.github.PullRequest.getTitle
import static pipelines.github.PullRequest.getPullRequest
import static pipelines.github.PullRequest.getPullRequestNumber
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions

/**
 * Tests for the PullRequest class.
 */
class PullRequestTest {

    private static final String OWNER = 'Chewy-Inc'
    private static final String REPO = 'jenkins-cicd'
    private static final String TOKEN = 'token'
    private static final int PR_NUMBER = 123
    private static final String COMMENT = 'Test comment'
    private static final String COMMENT_ID = 'testid'
    private static final String BRANCH_NAME = 'Pfeature-branch'
    private static final String BASE = 'main'

    @BeforeEach
    void setup() {
        // Reset any previous metaclass modifications
        Api.metaClass = null
        pipelines.nullability.StringNullability.metaClass = null
    }

    @AfterEach
    void cleanup() {
        // Clean up metaclass modifications after each test
        Api.metaClass = null
        pipelines.nullability.StringNullability.metaClass = null
    }

    @Test
    void "updateComment should delete and post if comment is not blank"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body -> 'posted' }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = updateComment(OWNER, REPO, TOKEN, PR_NUMBER, COMMENT, COMMENT_ID)
        assert result == 'posted'
    }

    @Test
    void "updateComment should return null if comment is blank"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = updateComment(OWNER, REPO, TOKEN, PR_NUMBER, '', COMMENT_ID)
        assert result == null
    }

    @Test
    void "updateComment should return null if comment is null"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = updateComment(OWNER, REPO, TOKEN, PR_NUMBER, null, COMMENT_ID)
        assert result == null
    }

    @Test
    void "updateComment should handle blank id"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body -> 'posted' }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = updateComment(OWNER, REPO, TOKEN, PR_NUMBER, COMMENT, '')
        assert result == 'posted'
    }

    @Test
    void "listComments should filter by id"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_testid something'],
                [body: 'other'],
            ]
        }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = listComments(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_testid')
        assert result.size() == 1
        assert result[0].body.contains('releng-update-comment_testid')
    }

    @Test
    void "deleteComment should delete if found"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_testid something', id: 1]
            ]
        }
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_testid')
        assert result == true
    }

    @Test
    void "deleteComment should return false if not found"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_testid')
        assert result == false
    }

    @Test
    void "getTitle should return title"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [title: 'PR Title'] }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = getTitle(OWNER, REPO, TOKEN, PR_NUMBER)
        assert result == 'PR Title'
    }

    @Test
    void "getPullRequest should return PR object"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [title: 'PR Title'] }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = getPullRequest(OWNER, REPO, TOKEN, PR_NUMBER)
        assert result.title == 'PR Title'
    }

    @Test
    void "getPullRequestNumber should return number if one PR found"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [[number: '123']] }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        pipelines.nullability.StringNullability.metaClass.static.assertNotBlank = { String s -> s }

        def result = getPullRequestNumber(OWNER, REPO, TOKEN, BRANCH_NAME, BASE)
        assert result == '123'
    }

    @Test
    void "getPullRequestNumber should return null if not exactly one PR found"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        pipelines.nullability.StringNullability.metaClass.static.assertNotBlank = { String s -> s }

        def result = getPullRequestNumber(OWNER, REPO, TOKEN, BRANCH_NAME, BASE)
        assert result == null
    }

    @Test
    void "guardPrNumber should throw for non-positive"() {
        try {
            PullRequest.guardPrNumber(0)
            Assertions.fail('Expected AssertionError for non-positive PR number')
        } catch (AssertionError e) {
            assert e.message.contains('Pull request number must be positive')
        }
    }

    @Test
    void "guardPrNumber should throw for negative"() {
        try {
            PullRequest.guardPrNumber(-1)
            Assertions.fail('Expected AssertionError for negative PR number')
        } catch (AssertionError e) {
            assert e.message.contains('Pull request number must be positive')
        }
    }

    @Test
    void "guardPrNumber should not throw for positive"() {
        PullRequest.guardPrNumber(1) // Should not throw
    }

    @Test
    void "listComments should return all comments when no filterId provided"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'comment 1'],
                [body: 'comment 2'],
                [body: 'comment 3'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        def result = listComments(OWNER, REPO, TOKEN, PR_NUMBER, null)
        assert result.size() == 3
        assert result[0].body == 'comment 1'
        assert result[1].body == 'comment 2'
        assert result[2].body == 'comment 3'
    }

    @Test
    void "listComments should return all comments when filterId not provided"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'comment 1'],
                [body: 'comment 2'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        def result = listComments(OWNER, REPO, TOKEN, PR_NUMBER)
        assert result.size() == 2
    }

    @Test
    void "getPullRequestNumber should return null if multiple PRs found"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [number: '123'],
                [number: '456'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }
        pipelines.nullability.StringNullability.metaClass.static.assertNotBlank = { String s -> s }

        def result = getPullRequestNumber(OWNER, REPO, TOKEN, BRANCH_NAME, BASE)
        assert result == null
    }

    @Test
    void "listComments should handle empty response"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }
        Api.metaClass.static.guardInput = { args -> }

        def result = listComments(OWNER, REPO, TOKEN, PR_NUMBER)
        assert result.size() == 0
    }

    @Test
    void "getPullRequestNumber should return null for empty response"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }
        Api.metaClass.static.guardInput = { args -> }

        def result = getPullRequestNumber(OWNER, REPO, TOKEN, BRANCH_NAME, BASE)
        assert result == null
    }

    @Test
    void "deleteComment should handle specific comment id correctly"() {
        def deletedUrl = null
        Api.metaClass.static.deleteFromGithub = { String url, String token ->
            deletedUrl = url
            return true
        }
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_testid something', id: COMMENT_ID]
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_testid')
        assert deletedUrl.contains("${OWNER}/${REPO}/issues/comments/${COMMENT_ID}")
    }

    @Test
    void "getPullRequest should return single PR when found"() {
        def mockPR = [number: 123, title: 'Test PR']
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> mockPR }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.getPullRequest(OWNER, REPO, TOKEN, PR_NUMBER)
        assert result.number == 123
        assert result.title == 'Test PR'
    }

    // ========== NEW TESTS TO IMPROVE COVERAGE ==========

    @Test
    void "updateComment should work with 5-parameter version"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body -> 'posted' }
        Api.metaClass.static.guardInput = { args -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, COMMENT)
        assert result == 'posted'
    }

    @Test
    void "listComments should work with 4-parameter version"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'comment 1'],
                [body: 'comment 2'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.listComments(OWNER, REPO, TOKEN, PR_NUMBER)
        assert result.size() == 2
    }

    @Test
    void "deleteComment should handle specific comment id properly"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'specific-id')
        assert result == false
    }

    @Test
    void "updateComment should handle edge case with empty string id"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body -> 'posted with empty id' }
        Api.metaClass.static.guardInput = { args -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, COMMENT, '')
        assert result == 'posted with empty id'
    }

    @Test
    void "listComments should return all comments when filterId is explicitly null"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'comment 1'],
                [body: 'comment 2'],
                [body: 'comment 3'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.listComments(OWNER, REPO, TOKEN, PR_NUMBER, null)
        assert result.size() == 3
    }

    @Test
    void "deleteComment should handle comment without id field"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_testid something'] // No id field
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_testid')
        assert result == false
    }

    @Test
    void "constructor test for coverage"() {
        // Test constructor for coverage - even though it's implicit
        def pullRequest = new PullRequest()
        assert pullRequest != null
    }

    // ========== ADDITIONAL TESTS FOR BETTER BRANCH COVERAGE ==========

    @Test
    void "guardPrNumber should handle edge cases for better branch coverage"() {
        // Test negative numbers for complete branch coverage
        try {
            PullRequest.guardPrNumber(-5)
            Assertions.fail("Should have thrown AssertionError")
        } catch (AssertionError e) {
            assert e.message.contains('Pull request number must be positive')
        }

        try {
            PullRequest.guardPrNumber(Integer.MIN_VALUE)
            Assertions.fail("Should have thrown AssertionError")
        } catch (AssertionError e) {
            assert e.message.contains('Pull request number must be positive')
        }

        // Test edge case of zero
        try {
            PullRequest.guardPrNumber(0)
            Assertions.fail("Should have thrown AssertionError")
        } catch (AssertionError e) {
            assert e.message.contains('Pull request number must be positive')
        }

        // Test large positive numbers (should pass)
        PullRequest.guardPrNumber(Integer.MAX_VALUE) // Should not throw
        PullRequest.guardPrNumber(999999) // Should not throw
    }

    @Test
    void "updateComment should handle defaulted parameter correctly"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body -> 'posted with default id' }
        Api.metaClass.static.guardInput = { args -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        // Test with default id parameter (not passing the 6th parameter)
        def result = PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, COMMENT)
        assert result == 'posted with default id'
    }

    @Test
    void "listComments should handle default parameter correctly"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'test comment 1'],
                [body: 'test comment 2'],
                [body: 'test comment 3'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        // Test with default filterId parameter (not passing the 5th parameter)
        def result = PullRequest.listComments(OWNER, REPO, TOKEN, PR_NUMBER)
        assert result.size() == 3
    }

    @Test
    void "deleteComment should handle edge case with missing comment properly"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            // Return comment without id field to test that branch
            [
                [body: 'releng-update-comment_testid something'] // Missing id field
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_testid')
        assert result == false
    }

    // ========== ADDITIONAL TESTS FOR ERROR PATHS AND EDGE CASES ==========

    @Test
    void "getTitle should handle API error gracefully"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            throw new IllegalStateException("API Error")
        }
        Api.metaClass.static.guardInput = { args -> }

        try {
            PullRequest.getTitle(OWNER, REPO, TOKEN, PR_NUMBER)
            Assertions.fail("Should have thrown IllegalStateException")
        } catch (IllegalStateException e) {
            assert e.message == "API Error"
        }
    }

    @Test
    void "getPullRequest should handle API error gracefully"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            throw new IllegalStateException("GitHub API Error")
        }
        Api.metaClass.static.guardInput = { args -> }

        try {
            PullRequest.getPullRequest(OWNER, REPO, TOKEN, PR_NUMBER)
            Assertions.fail("Should have thrown IllegalStateException")
        } catch (IllegalStateException e) {
            assert e.message == "GitHub API Error"
        }
    }

    @Test
    void "updateComment should handle deleteComment failure gracefully"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token ->
            throw new IllegalStateException("Delete failed")
        }
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_testid something', id: 1]
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        try {
            PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, COMMENT, COMMENT_ID)
            Assertions.fail("Should have thrown IllegalStateException")
        } catch (IllegalStateException e) {
            assert e.message == "Delete failed"
        }
    }

    @Test
    void "updateComment should handle postToGithub failure gracefully"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body ->
            throw new IllegalStateException("Post failed")
        }
        Api.metaClass.static.guardInput = { args -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        try {
            PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, COMMENT, COMMENT_ID)
            Assertions.fail("Should have thrown IllegalStateException")
        } catch (IllegalStateException e) {
            assert e.message == "Post failed"
        }
    }

    @Test
    void "listComments should handle API error gracefully"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            throw new IllegalStateException("List API Error")
        }
        Api.metaClass.static.guardInput = { args -> }

        try {
            PullRequest.listComments(OWNER, REPO, TOKEN, PR_NUMBER, 'filter')
            Assertions.fail("Should have thrown IllegalStateException")
        } catch (IllegalStateException e) {
            assert e.message == "List API Error"
        }
    }

    @Test
    void "deleteComment should handle API error in listComments"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            throw new IllegalStateException("List failed for delete")
        }
        Api.metaClass.static.guardInput = { args -> }

        try {
            PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'test-id')
            Assertions.fail("Should have thrown IllegalStateException")
        } catch (IllegalStateException e) {
            assert e.message == "List failed for delete"
        }
    }

    @Test
    void "guardPrNumber should handle boundary values comprehensively"() {
        // Test boundary value: 1 (smallest positive)
        PullRequest.guardPrNumber(1) // Should not throw

        // Test boundary value: just above zero
        PullRequest.guardPrNumber(2) // Should not throw

        // Test with very small negative
        try {
            PullRequest.guardPrNumber(-1)
            Assertions.fail("Should have thrown AssertionError")
        } catch (AssertionError e) {
            assert e.message.contains('Pull request number must be positive')
        }
    }

    @Test
    void "updateComment should test the comment blank branch"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.guardInput = { args -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        // Test with a string that is blank (has whitespace but evaluates to blank)
        def result = PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, '   ', COMMENT_ID)
        assert result == null
    }

    @Test
    void "updateComment should properly handle non-blank comments"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body -> 'successfully posted' }
        Api.metaClass.static.guardInput = { args -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, 'Valid comment', COMMENT_ID)
        assert result == 'successfully posted'
    }

    @Test
    void "listComments should test filterId branch when filterId is provided"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_specific matching comment'],
                [body: 'other comment that does not match'],
                [body: 'another releng-update-comment_specific match'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.listComments(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_specific')
        assert result.size() == 2
        assert result.every { it.body.contains('releng-update-comment_specific') }
    }

    @Test
    void "listComments should test null filterId branch explicitly"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'comment 1'],
                [body: 'comment 2'],
                [body: 'comment 3'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        // Explicitly pass null to test the null branch
        def result = PullRequest.listComments(OWNER, REPO, TOKEN, PR_NUMBER, null)
        assert result.size() == 3
    }

    @Test
    void "deleteComment should test the comments empty branch"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'nonexistent-id')
        assert result == false
    }

    @Test
    void "deleteComment should test the commentToDelete id branch when id exists"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_test-id matching comment', id: 12345]
            ]
        }
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'test-id')
        assert result == true
    }

    @Test
    void "deleteComment should test the commentToDelete id branch when id is null"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_test-id matching comment', id: null]
            ]
        }
        Api.metaClass.static.guardInput = { args -> }

        def result = PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'test-id')
        assert result == false
    }

    @Test
    void "getPullRequestNumber should test pulls size equals 1 branch"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [[number: 'single-pr-number']]
        }
        Api.metaClass.static.guardInput = { args -> }
        pipelines.nullability.StringNullability.metaClass.static.assertNotBlank = { String s -> s }

        def result = PullRequest.getPullRequestNumber(OWNER, REPO, TOKEN, BRANCH_NAME, BASE)
        assert result == 'single-pr-number'
    }

    @Test
    void "getPullRequestNumber should test pulls size not 1 branch with empty list"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }
        Api.metaClass.static.guardInput = { args -> }
        pipelines.nullability.StringNullability.metaClass.static.assertNotBlank = { String s -> s }

        def result = PullRequest.getPullRequestNumber(OWNER, REPO, TOKEN, BRANCH_NAME, BASE)
        assert result == null
    }

    @Test
    void "getPullRequestNumber should test pulls size not 1 branch with multiple items"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [number: 'pr-1'],
                [number: 'pr-2'],
                [number: 'pr-3'],
            ]
        }
        Api.metaClass.static.guardInput = { args -> }
        pipelines.nullability.StringNullability.metaClass.static.assertNotBlank = { String s -> s }

        def result = PullRequest.getPullRequestNumber(OWNER, REPO, TOKEN, BRANCH_NAME, BASE)
        assert result == null
    }

    // ========== ADDITIONAL TESTS TO REACH 80% COVERAGE ==========

    @Test
    void "hiddenText should format content correctly when accessed indirectly"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body ->
            // Verify that the hidden text is properly formatted
            assert body.contains('<!-- "releng-update-comment_test-id" -->')
            return 'posted with hidden text'
        }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, 'Test comment', 'test-id')
        assert result == 'posted with hidden text'
    }

    @Test
    void "commentId should format id correctly when accessed indirectly"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_custom-test-id matching comment', id: 99999]
            ]
        }
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_custom-test-id')
        assert result == true
    }

    @Test
    void "guardComment should validate prNumber through guardPrNumber"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [title: 'Test PR'] }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        // This should work fine with positive PR number
        def result = PullRequest.getPullRequest(OWNER, REPO, TOKEN, 1)
        assert result.title == 'Test PR'

        // Test that guardPrNumber is called by trying an invalid PR number
        try {
            PullRequest.getPullRequest(OWNER, REPO, TOKEN, 0)
            Assertions.fail("Should have thrown AssertionError for invalid PR number")
        } catch (AssertionError e) {
            assert e.message.contains('Pull request number must be positive')
        }
    }

    @Test
    void "updateComment should handle complex comment content with special characters"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body ->
            // Verify complex content is handled
            assert body.contains('Complex comment with "quotes" and \n newlines')
            return 'posted complex content'
        }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def complexComment = 'Complex comment with "quotes" and \n newlines'
        def result = PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, complexComment, 'complex-id')
        assert result == 'posted complex content'
    }

    @Test
    void "deleteComment should handle multiple comments with same filter"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_multi matching comment 1', id: 101],
                [body: 'releng-update-comment_multi matching comment 2', id: 102],
                [body: 'other comment', id: 103],
            ]
        }
        Api.metaClass.static.deleteFromGithub = { String url, String token ->
            // Should delete the first matching comment (id: 101)
            assert url.contains('/101')
            return true
        }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_multi')
        assert result == true
    }

    @Test
    void "listComments should handle filtering with special characters in filterId"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_test@123 special chars'],
                [body: 'releng-update-comment_test@456 other special chars'],
                [body: 'normal comment'],
            ]
        }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = PullRequest.listComments(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_test@')
        assert result.size() == 2
        assert result.every { it.body.contains('releng-update-comment_test@') }
    }

    @Test
    void "getPullRequestNumber should handle edge case with null pulls response"() {
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> null }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        pipelines.nullability.StringNullability.metaClass.static.assertNotBlank = { String s -> s }

        def result = PullRequest.getPullRequestNumber(OWNER, REPO, TOKEN, BRANCH_NAME, BASE)
        assert result == null
    }

    @Test
    void "updateComment should test exact comment prefix formatting"() {
        Api.metaClass.static.deleteFromGithub = { String url, String token -> true }
        Api.metaClass.static.postToGithub = { String url, String token, String body ->
            // Verify exact formatting of the prefixed comment
            assert body.startsWith('<!-- "releng-update-comment_exact-test" -->\n\n')
            assert body.endsWith('Exact test comment')
            return 'posted with exact formatting'
        }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }
        Api.metaClass.static.fetchFromGitHub = { String url, String token -> [] }

        def result = PullRequest.updateComment(OWNER, REPO, TOKEN, PR_NUMBER, 'Exact test comment', 'exact-test')
        assert result == 'posted with exact formatting'
    }

    @Test
    void "deleteComment should verify exact URL construction for comment deletion"() {
        def capturedUrl = null
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            [
                [body: 'releng-update-comment_url-test matching comment', id: 54321]
            ]
        }
        Api.metaClass.static.deleteFromGithub = { String url, String token ->
            capturedUrl = url
            return true
        }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        PullRequest.deleteComment(OWNER, REPO, TOKEN, PR_NUMBER, 'releng-update-comment_url-test')

        assert capturedUrl == "${OWNER}/${REPO}/issues/comments/54321"
    }

    @Test
    void "getPullRequest should verify exact URL construction"() {
        def capturedUrl = null
        Api.metaClass.static.fetchFromGitHub = { String url, String token ->
            capturedUrl = url
            return [title: 'URL Test PR', number: PR_NUMBER]
        }
        Api.metaClass.static.guardInput = { String owner, String repo, String token -> }

        def result = PullRequest.getPullRequest(OWNER, REPO, TOKEN, PR_NUMBER)

        assert capturedUrl == "${OWNER}/${REPO}/pulls/${PR_NUMBER}"
        assert result.title == 'URL Test PR'
    }

}
