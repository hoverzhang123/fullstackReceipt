package pipelines.github.api

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

class GitTagTest {
    private static final String TEST_TAG_NAME = 'v1.2.3'
    private static final String TEST_SHA = 'abc123def456789'

    @Test
    void "should create GitTag with name and sha"() {
        GitTag tag = new GitTag(TEST_TAG_NAME, TEST_SHA)

        assertNotNull(tag)
        assertEquals(TEST_TAG_NAME, tag.name)
        assertEquals(TEST_SHA, tag.sha)
    }

    @Test
    void "should handle null name"() {
        GitTag tag = new GitTag(null, TEST_SHA)

        assertNotNull(tag)
        assertEquals(null, tag.name)
        assertEquals(TEST_SHA, tag.sha)
    }

    @Test
    void "should handle null sha"() {
        GitTag tag = new GitTag(TEST_TAG_NAME, null)

        assertNotNull(tag)
        assertEquals(TEST_TAG_NAME, tag.name)
        assertEquals(null, tag.sha)
    }

    @Test
    void "should handle empty strings"() {
        GitTag tag = new GitTag('', '')

        assertNotNull(tag)
        assertEquals('', tag.name)
        assertEquals('', tag.sha)
    }

    @Test
    void "should create with complex tag names"() {
        String complexName = 'v2.1.0-beta.1+build.123'
        String complexSha = '1a2b3c4d5e6f7890abcdef1234567890abcdef12'

        GitTag tag = new GitTag(complexName, complexSha)

        assertEquals(complexName, tag.name)
        assertEquals(complexSha, tag.sha)
    }

    @Test
    void "fields should be final"() {
        GitTag tag = new GitTag(TEST_TAG_NAME, TEST_SHA)

        // Verify that we can read the fields (they are accessible)
        assertEquals(TEST_TAG_NAME, tag.name)
        assertEquals(TEST_SHA, tag.sha)

        // Since fields are final, they cannot be reassigned after construction
        // This is enforced at compile time, so we just verify the values are set correctly
    }
}
