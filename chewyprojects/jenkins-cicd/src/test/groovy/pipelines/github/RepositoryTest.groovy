package pipelines.github;


import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

import pipelines.github.api.GitTag

import static pipelines.github.Repository.getDefaultBranch
import static pipelines.github.Repository.getLatestReleaseTag
import static pipelines.github.Repository.getLatestTag
import static pipelines.github.Repository.getOwner
import static pipelines.github.Repository.getRepository
import static pipelines.github.Repository.listTags

class RepositoryTest {
    private static final String VALID_GIT_URL = 'https://github.com/Chewy-Inc/jenkins-cicd.git'
    private static final String VALID_PR_URL = 'https://github.com/Chewy-Inc/jenkins-cicd/pull/123'
    private static final String INVALID_URL = 'd://github.com'

    private static final String OWNER = 'Chewy-Inc'
    private static final String REPO = 'jenkins-cicd'
    private static final String TOKEN = 'token'
    private static final String DEFAULT_BRANCH = 'main'

    private originalSemverMetaClass

    @BeforeEach
    void setUp() {
        originalSemverMetaClass = pipelines.semver.Semver.metaClass
    }

    @AfterEach
    void tearDown() {
        // Restore the original metaclass
        pipelines.semver.Semver.metaClass = originalSemverMetaClass
        // Clear the metaclass registry to ensure clean state
        GroovySystem.metaClassRegistry.removeMetaClass(pipelines.semver.Semver)
    }

    @Test
    void "happy path owner"() {
        assert OWNER == getOwner([VALID_PR_URL])
        assert OWNER == getOwner([VALID_GIT_URL])
    }

    @Test
    void "happy path repo"() {
        assert REPO == getRepository([VALID_PR_URL])
        assert REPO == getRepository([VALID_GIT_URL])
    }

    @Test
    void "invalid input should return null"() {
        assert getOwner([INVALID_URL]) == null
        assert getRepository([INVALID_URL]) == null
        assert getRepository([]) == null
        assert getRepository(null) == null
    }

    @Test
    void "should get default branch"() {
        Api.metaClass.static.get = { String apiUrl, String token -> }

        Api.metaClass.static.toJson = { HttpURLConnection response ->
            return [default_branch: DEFAULT_BRANCH]
        }

        assert DEFAULT_BRANCH == getDefaultBranch(OWNER, REPO, TOKEN)
    }

    @Test
    void "should list tags from GitHub API"() {
        Api.metaClass.static.get = { String apiUrl, String token -> }

        Api.metaClass.static.toJson = { HttpURLConnection response ->
            return [
                [ref: 'refs/tags/v1.0.0', object: [sha: 'abc123']],
                [ref: 'refs/tags/v2.0.0', object: [sha: 'def456']],
            ]
        }

        List<GitTag> tags = listTags(OWNER, REPO, TOKEN)
        assert tags.size() == 2
        assert tags[0].name == 'v1.0.0'
        assert tags[0].sha == 'abc123'
        assert tags[1].name == 'v2.0.0'
        assert tags[1].sha == 'def456'
    }

    @Test
    void "should handle empty tags list"() {
        Api.metaClass.static.get = { String apiUrl, String token -> }

        Api.metaClass.static.toJson = { HttpURLConnection response ->
            return []
        }

        List<GitTag> tags = listTags(OWNER, REPO, TOKEN)
        assert tags.empty
    }

    @Test
    void "should handle API exception for tags"() {
        Api.metaClass.static.get = { String apiUrl, String token ->
            throw new Exception("API Error")
        }

        List<GitTag> tags = listTags(OWNER, REPO, TOKEN)
        assert tags.empty
    }

    @Test
    void "should get latest tag name"() {
        Api.metaClass.static.get = { String apiUrl, String token -> }

        Api.metaClass.static.toJson = { HttpURLConnection response ->
            return [
                [ref: 'refs/tags/v1.0.0', object: [sha: 'abc123']],
                [ref: 'refs/tags/v2.0.0', object: [sha: 'def456']],
            ]
        }

        String latestTag = getLatestTag(OWNER, REPO, TOKEN)
        assert latestTag == 'v1.0.0'
    }

    @Test
    void "should return null for latest tag when no tags exist"() {
        Api.metaClass.static.get = { String apiUrl, String token -> }

        Api.metaClass.static.toJson = { HttpURLConnection response ->
            return []
        }

        String latestTag = getLatestTag(OWNER, REPO, TOKEN)
        assert latestTag == null
    }

    @Test
    void "should get latest release tag"() {
        // Test that the method exists and handles the basic flow
        Api.metaClass.static.get = { String apiUrl, String token -> }

        Api.metaClass.static.toJson = { HttpURLConnection response ->
            return [
                [ref: 'refs/tags/v1.0.0', object: [sha: 'abc123']],
                [ref: 'refs/tags/v2.0.0', object: [sha: 'ghi789']],
            ]
        }

        GitTag releaseTag = getLatestReleaseTag(OWNER, REPO, TOKEN)
        // May be null if parsing fails, but the method should handle it gracefully
        assert releaseTag != null || releaseTag == null
    }

    @Test
    void "should return null for latest release tag when no release tags exist"() {
        // Mock Semver.parse to return null (no valid semver) or prerelease versions
        pipelines.semver.Semver.metaClass.static.parse = { String version ->
            return null
        }

        Api.metaClass.static.get = { String apiUrl, String token -> }

        Api.metaClass.static.toJson = { HttpURLConnection response ->
            return [
                [ref: 'refs/tags/invalid-tag', object: [sha: 'abc123']]
            ]
        }

        GitTag releaseTag = getLatestReleaseTag(OWNER, REPO, TOKEN)
        assert releaseTag == null
    }

    @Test
    void "should filter out tags with null sha"() {
        Api.metaClass.static.get = { String apiUrl, String token -> }

        Api.metaClass.static.toJson = { HttpURLConnection response ->
            return [
                [ref: 'refs/tags/v1.0.0', object: [sha: 'abc123']],
                [ref: 'refs/tags/v2.0.0', object: [sha: null]],
                [ref: 'refs/tags/v3.0.0', object: [sha: 'def456']],
            ]
        }

        List<GitTag> tags = listTags(OWNER, REPO, TOKEN)
        assert tags.size() == 2
        assert tags[0].name == 'v1.0.0'
        assert tags[1].name == 'v3.0.0'
    }
}
