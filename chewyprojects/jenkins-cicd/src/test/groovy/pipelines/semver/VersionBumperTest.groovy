package pipelines.semver

import static pipelines.semver.Semver.parse
import static pipelines.semver.VersionBumper.autoBump
import static pipelines.semver.VersionBumper.shouldBumpMajor
import static pipelines.semver.VersionBumper.shouldBumpMinor
import static pipelines.semver.VersionBumper.shouldBumpPatch
import static pipelines.semver.VersionBumper.bumpByMessage

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class VersionBumperTest {

    private static final String MAJOR_BUMP_VERSION = '2.0.0'
    private static final String MINOR_BUMP_VERSION = '1.4.0'
    private static final String PATCH_BUMP_VERSION = '1.3.6'
    private final String MAJOR_PREFERENCE = 'major'
    private final String MINOR_PREFERENCE = 'minor'
    private final String PATCH_PREFERENCE = 'patch'
    private final String MAJOR_COMMIT = '[major]'
    private final String MINOR_COMMIT = '[minor]'
    private final String PATCH_COMMIT = '[patch]'

    private final Semver semver = parse('1.3.5')

    @Test
    void "shouldBumpMajor returns true when input contains Major with any case"() {
        assertTrue(shouldBumpMajor('[Major] Breaking change'))
        assertTrue(shouldBumpMajor('Some [MAJOR] breaking change'))
        assertTrue(shouldBumpMajor('Breaking change [major]'))
        assertTrue(shouldBumpMajor('[MaJoR] Mixed case works too'))
    }

    @Test
    void "shouldBumpMajor returns false when input does not contain Major"() {
        assertFalse(shouldBumpMajor(null))
        assertFalse(shouldBumpMajor(''))
        assertFalse(shouldBumpMajor('Breaking change'))
        assertFalse(shouldBumpMajor('[Minor] New feature'))
        assertFalse(shouldBumpMajor('[Patch] Bug fix'))
    }

    @Test
    void "shouldBumpMinor returns true when input contains Minor with any case"() {
        assertTrue(shouldBumpMinor('[Minor] New feature'))
        assertTrue(shouldBumpMinor('Some [MINOR] new feature'))
        assertTrue(shouldBumpMinor('New feature [minor]'))
        assertTrue(shouldBumpMinor('[MiNoR] Mixed case works too'))
    }

    @Test
    void "shouldBumpMinor returns false when input does not contain Minor"() {
        assertFalse(shouldBumpMinor(null))
        assertFalse(shouldBumpMinor(''))
        assertFalse(shouldBumpMinor('New feature'))
        assertFalse(shouldBumpMinor('[Major] Breaking change'))
        assertFalse(shouldBumpMinor('[Patch] Bug fix'))
    }

    @Test
    void "shouldBumpPatch returns true when input contains Patch with any case"() {
        assertTrue(shouldBumpPatch('[Patch] Bug fix'))
        assertTrue(shouldBumpPatch('Some [PATCH] bug fix'))
        assertTrue(shouldBumpPatch('Bug fix [patch]'))
        assertTrue(shouldBumpPatch('[PaTcH] Mixed case works too'))
    }

    @Test
    void "shouldBumpPatch returns false when input does not contain Patch"() {
        assertFalse(shouldBumpPatch(null))
        assertFalse(shouldBumpPatch(''))
        assertFalse(shouldBumpPatch('Bug fix'))
        assertFalse(shouldBumpPatch('[Major] Breaking change'))
        assertFalse(shouldBumpPatch('[Minor] New feature'))
    }

    @Test
    void "autoBump by preference"() {
        assertEquals(MAJOR_BUMP_VERSION, autoBump(semver, null, MAJOR_PREFERENCE).version)
        assertEquals(MINOR_BUMP_VERSION, autoBump(semver, null, MINOR_PREFERENCE).version)
        assertEquals(PATCH_BUMP_VERSION, autoBump(semver, null, PATCH_PREFERENCE).version)
        assertEquals(MINOR_BUMP_VERSION, autoBump(semver, null, null).version)
    }

    @Test
    void "autoBump by message"() {
        assertEquals(MAJOR_BUMP_VERSION, autoBump(semver, MAJOR_COMMIT, MAJOR_PREFERENCE).version)
        assertEquals(MINOR_BUMP_VERSION, autoBump(semver, MINOR_COMMIT, MINOR_PREFERENCE).version)
        assertEquals(PATCH_BUMP_VERSION, autoBump(semver, PATCH_COMMIT, PATCH_PREFERENCE).version)
        assertEquals(MINOR_BUMP_VERSION, autoBump(semver, '', null).version)
    }

    @Test
    void "bumpByMessage"() {
        assertEquals(MAJOR_BUMP_VERSION, bumpByMessage(semver, MAJOR_COMMIT).version)
        assertEquals(MINOR_BUMP_VERSION, bumpByMessage(semver, MINOR_COMMIT).version)
        assertEquals(PATCH_BUMP_VERSION, bumpByMessage(semver, PATCH_COMMIT).version)
        assertNull(bumpByMessage(semver, null))
    }
}
