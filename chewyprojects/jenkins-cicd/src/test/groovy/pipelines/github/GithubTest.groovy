package pipelines.github

import static pipelines.github.Github.token
import static pipelines.github.Github.updateComment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

/**
 * Tests for the Github class methods.
 */
class GithubTest {

    private static final String TEST_TOKEN = 'test-github-token'
    private static final String OWNER = 'test-owner'
    private static final String REPO = 'test-repo'
    private static final String COMMENT = 'Test comment'
    private static final String BASE_BRANCH = 'main'
    private static final String PR_NUMBER_STR = '123'
    private static final int PR_NUMBER = 123
    private static final String BRANCH_NAME = 'feature/test-branch'

    private MockScript mockScript
    private originalRepositoryMetaClass
    private originalPullRequestMetaClass
    private originalAuthMetaClass
    private originalEnvironmentMetaClass

    @BeforeEach
    void setUp() {
        mockScript = new MockScript()

        // Store original metaclasses
        originalRepositoryMetaClass = Repository.metaClass
        originalPullRequestMetaClass = PullRequest.metaClass
        originalAuthMetaClass = Auth.metaClass
        originalEnvironmentMetaClass = pipelines.jenkins.Environment.metaClass

        setupMocks()
    }

    @AfterEach
    void tearDown() {
        // Restore original metaclasses
        Repository.metaClass = originalRepositoryMetaClass
        PullRequest.metaClass = originalPullRequestMetaClass
        Auth.metaClass = originalAuthMetaClass
        pipelines.jenkins.Environment.metaClass = originalEnvironmentMetaClass

        // Clear metaclass registry
        GroovySystem.metaClassRegistry.removeMetaClass(Repository)
        GroovySystem.metaClassRegistry.removeMetaClass(PullRequest)
        GroovySystem.metaClassRegistry.removeMetaClass(Auth)
        GroovySystem.metaClassRegistry.removeMetaClass(pipelines.jenkins.Environment)
    }

    @Test
    void shouldGetTokenFromEnvironment() {
        mockScript.env.GH_TOKEN = TEST_TOKEN

        String result = token(mockScript.env)

        assert result == TEST_TOKEN
    }

    @Test
    void shouldReturnNullWhenTokenNotInEnvironment() {
        mockScript.env.GH_TOKEN = null

        String result = token(mockScript.env)

        assert result == ''
    }

    @Test
    void shouldUpdateCommentWithProvidedParameters() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert owner == OWNER
            assert repo == REPO
            assert token == TEST_TOKEN
            assert prNumber == PR_NUMBER
            assert comment == COMMENT
            assert id == PR_NUMBER_STR
            return 'success'
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, PR_NUMBER_STR, PR_NUMBER_STR, OWNER, REPO)

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldExtractOwnerAndRepoFromEnvironmentWhenNotProvided() {
        Repository.metaClass.static.getOwner = { List<String> urls ->
            assert urls.contains('https://github.com/test-owner/test-repo.git')
            assert urls.contains('https://github.com/test-owner/test-repo/pull/123')
            return OWNER
        }

        Repository.metaClass.static.getRepository = { List<String> urls ->
            assert urls.contains('https://github.com/test-owner/test-repo.git')
            assert urls.contains('https://github.com/test-owner/test-repo/pull/123')
            return REPO
        }

        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert owner == OWNER
            assert repo == REPO
            return 'success'
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, PR_NUMBER_STR, PR_NUMBER_STR)

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldUsePrNumberWhenProvidedAndValid() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert prNumber == PR_NUMBER
            return 'success'
        }

        updateComment(mockScript, COMMENT, BASE_BRANCH, PR_NUMBER_STR, PR_NUMBER_STR, OWNER, REPO)

        assert updateCommentCalled
    }

    @Test
    void shouldFallbackToChangeIdWhenPrNumberIs0() {
        mockScript.env.CHANGE_ID = '456'

        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert prNumber == 456
            return 'success'
        }

        updateComment(mockScript, COMMENT, BASE_BRANCH, '0', 'test-id', OWNER, REPO)

        assert updateCommentCalled
    }

    @Test
    void shouldTryToGetPrNumberFromBranchWhenPrNumberIsInvalid() {
        mockScript.env.BRANCH_NAME = BRANCH_NAME
        mockScript.env.CHANGE_ID = null

        boolean getPullRequestNumberCalled = false
        PullRequest.metaClass.static.getPullRequestNumber = { String owner, String repo, String token, String branch, String baseBranch ->
            getPullRequestNumberCalled = true
            assert owner == OWNER
            assert repo == REPO
            assert token == TEST_TOKEN
            assert branch == BRANCH_NAME
            assert baseBranch == BASE_BRANCH
            return '789'
        }

        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert prNumber == 789
            return 'success'
        }

        updateComment(mockScript, COMMENT, BASE_BRANCH, '0', 'test-id', OWNER, REPO)

        assert getPullRequestNumberCalled
        assert updateCommentCalled
    }

    @Test
    void shouldTryMultipleBranchNamesWhenFirstFails() {
        mockScript.env.BRANCH_NAME = null
        mockScript.env.GIT_BRANCH = BRANCH_NAME
        mockScript.env.CHANGE_BRANCH = 'backup-branch'
        mockScript.env.CHANGE_ID = null

        int getPullRequestCallCount = 0
        PullRequest.metaClass.static.getPullRequestNumber = { String owner, String repo, String token, String branch, String baseBranch ->
            getPullRequestCallCount++
            if (branch == BRANCH_NAME) {
                return '999'
            }
            return null
        }

        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert prNumber == 999
            return 'success'
        }

        updateComment(mockScript, COMMENT, BASE_BRANCH, '0', 'test-id', OWNER, REPO)

        assert getPullRequestCallCount == 1
        assert updateCommentCalled
    }

    @Test
    void shouldHandleExceptionWhenGettingPrNumberFromBranch() {
        mockScript.env.BRANCH_NAME = BRANCH_NAME
        mockScript.env.CHANGE_ID = null

        PullRequest.metaClass.static.getPullRequestNumber = { String owner, String repo, String token, String branch, String baseBranch ->
            throw new IOException('API error')
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, '0', 'test-id', OWNER, REPO)

        assert result == null
        assert mockScript.echoMessages.size() == 2
        assert mockScript.echoMessages[0].contains("Failed to get PR number for branch ${BRANCH_NAME}: API error")
        assert mockScript.echoMessages[1].contains('Invalid PR number: 0, no comment will be posted')
    }

    @Test
    void shouldHandleIllegalArgumentExceptionWhenGettingPrNumberFromBranch() {
        mockScript.env.BRANCH_NAME = BRANCH_NAME
        mockScript.env.CHANGE_ID = null

        PullRequest.metaClass.static.getPullRequestNumber = { String owner, String repo, String token, String branch, String baseBranch ->
            throw new IllegalArgumentException("Invalid branch")
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, '0', 'test-id', OWNER, REPO)

        assert result == null
        assert mockScript.echoMessages.size() == 2
        assert mockScript.echoMessages[0].contains("Failed to get PR number for branch ${BRANCH_NAME}: Invalid branch")
        assert mockScript.echoMessages[1].contains("Invalid PR number: 0, no comment will be posted")
    }

    @Test
    void shouldReturnNullWhenPrNumberIsInvalidAndNoFallbackWorks() {
        mockScript.env.CHANGE_ID = null
        mockScript.env.BRANCH_NAME = null
        mockScript.env.GIT_BRANCH = null
        mockScript.env.CHANGE_BRANCH = null

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, '0', 'test-id', OWNER, REPO)

        assert result == null
        assert mockScript.echoMessages.size() == 1
        assert mockScript.echoMessages[0].contains('Invalid PR number: 0, no comment will be posted')
    }

    @Test
    void shouldUseDefaultBranchWhenBaseBranchNotProvided() {
        mockScript.env.BRANCH_NAME = BRANCH_NAME
        mockScript.env.CHANGE_ID = null

        boolean getDefaultBranchCalled = false
        Repository.metaClass.static.getDefaultBranch = { String owner, String repo, String token ->
            getDefaultBranchCalled = true
            assert owner == OWNER
            assert repo == REPO
            assert token == TEST_TOKEN
            return 'main'
        }

        PullRequest.metaClass.static.getPullRequestNumber = { String owner, String repo, String token, String branch, String baseBranch ->
            assert baseBranch == 'main'
            return '123'
        }

        updateComment(mockScript, COMMENT, null, '0', 'test-id', OWNER, REPO)

        assert getDefaultBranchCalled
    }

    @Test
    void shouldHandleNonIntegerPrNumberFromBranchLookup() {
        mockScript.env.BRANCH_NAME = BRANCH_NAME
        mockScript.env.CHANGE_ID = null

        PullRequest.metaClass.static.getPullRequestNumber = { String owner, String repo, String token, String branch, String baseBranch ->
            return 'invalid-number'
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, '0', 'test-id', OWNER, REPO)

        assert result == null
        assert mockScript.echoMessages.size() == 1
        assert mockScript.echoMessages[0].contains("Invalid PR number: 0, no comment will be posted")
    }

    @Test
    void shouldReturnFallbackIdWhenIdIsNull() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert id == '123'  // Should be the PR number as string
            return 'success'
        }

        updateComment(mockScript, COMMENT, BASE_BRANCH, PR_NUMBER_STR, null, OWNER, REPO)

        assert updateCommentCalled
    }

    @Test
    void shouldReturnProvidedIdWhenNotNull() {
        String customId = 'custom-id'

        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert id == customId
            return 'success'
        }

        updateComment(mockScript, COMMENT, BASE_BRANCH, PR_NUMBER_STR, customId, OWNER, REPO)

        assert updateCommentCalled
    }

    @Test
    void shouldCallUpdateCommentWithOnlyScriptParameter() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert comment == ''  // Default empty comment
            return 'success'
        }

        Object result = updateComment(mockScript)

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldCallUpdateCommentWithScriptAndCommentParameters() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert comment == COMMENT
            return 'success'
        }

        Object result = updateComment(mockScript, COMMENT)

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldCallUpdateCommentWithScriptCommentAndBaseBranchParameters() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert comment == COMMENT
            return 'success'
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH)

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldCallUpdateCommentWithScriptCommentBaseBranchAndPrNumberParameters() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert comment == COMMENT
            assert prNumber == PR_NUMBER
            return 'success'
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, PR_NUMBER_STR)

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldCallUpdateCommentWithScriptCommentBaseBranchPrNumberAndIdParameters() {
        String customId = 'test-id-123'
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert comment == COMMENT
            assert prNumber == PR_NUMBER
            assert id == customId
            return 'success'
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, PR_NUMBER_STR, customId)

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldCallUpdateCommentWithScriptCommentBaseBranchPrNumberIdAndGithubOwnerParameters() {
        String customId = 'test-id-456'
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert comment == COMMENT
            assert prNumber == PR_NUMBER
            assert id == customId
            assert owner == OWNER
            return 'success'
        }

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, PR_NUMBER_STR, customId, OWNER)

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldHandleInvalidPrNumberAndFallbackToChangeId() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert prNumber == 456  // Should use CHANGE_ID
            return 'success'
        }

        mockScript.env.CHANGE_ID = '456'

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, 'invalid-number')

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldHandleZeroPrNumberAndFallbackToChangeId() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert prNumber == 789  // Should use CHANGE_ID
            return 'success'
        }

        mockScript.env.CHANGE_ID = '789'

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, '0')

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldHandleNegativePrNumberAndFallbackToChangeId() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert prNumber == 111  // Should use CHANGE_ID
            return 'success'
        }

        mockScript.env.CHANGE_ID = '111'

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, '-5')

        assert result == 'success'
        assert updateCommentCalled
    }

    @Test
    void shouldReturnNullWhenBothPrNumberAndChangeIdAreInvalid() {
        mockScript.env.CHANGE_ID = 'invalid-change-id'

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, 'invalid-pr')

        assert result == null
        assert mockScript.echoMessages.any { it.contains('Invalid PR number: invalid-pr') }
    }

    @Test
    void shouldReturnNullWhenBothPrNumberAndChangeIdAreZeroOrNegative() {
        mockScript.env.CHANGE_ID = '-1'

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, '0')

        assert result == null
        assert mockScript.echoMessages.any { it.contains('Invalid PR number: 0') }
    }

    @Test
    void shouldReturnNullWhenPrNumberIsInvalidAndChangeIdIsNull() {
        mockScript.env.CHANGE_ID = null

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, 'not-a-number')

        assert result == null
        assert mockScript.echoMessages.any { it.contains('Invalid PR number: not-a-number') }
    }

    @Test
    void shouldUseValidPrNumberEvenWhenChangeIdExists() {
        boolean updateCommentCalled = false
        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            updateCommentCalled = true
            assert prNumber == 42  // Should use prNumber, not CHANGE_ID
            return 'success'
        }

        mockScript.env.CHANGE_ID = '999'

        Object result = updateComment(mockScript, COMMENT, BASE_BRANCH, '42')

        assert result == 'success'
        assert updateCommentCalled
    }

    private void setupMocks() {
        // Mock Environment methods
        pipelines.jenkins.Environment.metaClass.static.getGitUrl = { env ->
            return 'https://github.com/test-owner/test-repo.git'
        }

        pipelines.jenkins.Environment.metaClass.static.getChangeUrl = { env ->
            return 'https://github.com/test-owner/test-repo/pull/123'
        }

        pipelines.jenkins.Environment.metaClass.static.getValue = { env, key ->
            return env[key]
        }

        // Mock Repository methods
        Repository.metaClass.static.getOwner = { List<String> urls ->
            return OWNER
        }

        Repository.metaClass.static.getRepository = { List<String> urls ->
            return REPO
        }

        Repository.metaClass.static.getDefaultBranch = { String owner, String repo, String token ->
            return BASE_BRANCH
        }

        // Mock PullRequest methods
        PullRequest.metaClass.static.getPullRequestNumber = { String owner, String repo, String token, String branch, String baseBranch ->
            return null
        }

        PullRequest.metaClass.static.updateComment = { String owner, String repo, String token, int prNumber, String comment, String id ->
            return 'mocked-result'
        }

        // Mock Auth
        Auth.metaClass.static.withGithubAuth = { Script script, Closure closure ->
            return closure.call()
        }

        // Setup mock script environment
        mockScript.env.GH_TOKEN = TEST_TOKEN
        mockScript.env.CHANGE_ID = PR_NUMBER_STR
    }

    // Mock Script class for testing
    private static class MockScript extends Script {

        def env = [:]
        List<String> echoMessages = []

        void echo(String message) {
            echoMessages.add(message)
        }

        @Override
        Object run() {
            return null
        }

    }

}
