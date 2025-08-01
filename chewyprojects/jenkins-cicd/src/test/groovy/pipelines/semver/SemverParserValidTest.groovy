package pipelines.semver

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.params.provider.Arguments.of
import static pipelines.semver.Semver.parse

class SemverParserValidTest {
    static Stream<Arguments> data() {
        return Stream.of(
                of("Basic version", '0.0.4', 0G, 0G, 4G, null, null),
                of("Simple version", '1.2.3', 1G, 2G, 3G, null, null),
                of("Double digit version", '10.20.30', 10G, 20G, 30G, null, null),
                of("Pre-release with meta", '1.1.2-prerelease+meta', 1G, 1G, 2G, 'prerelease', 'meta'),
                of("Meta only", '1.1.2+meta', 1G, 1G, 2G, null, 'meta'),
                of("Meta with hyphen", '1.1.2+meta-valid', 1G, 1G, 2G, null, 'meta-valid'),
                of("Alpha pre-release", '1.0.0-alpha', 1G, 0G, 0G, 'alpha', null),
                of("Beta pre-release", '1.0.0-beta', 1G, 0G, 0G, 'beta', null),
                of("Alpha.beta pre-release", '1.0.0-alpha.beta', 1G, 0G, 0G, 'alpha.beta', null),
                of("Alpha.beta.1 pre-release", '1.0.0-alpha.beta.1', 1G, 0G, 0G, 'alpha.beta.1', null),
                of("Alpha.1 pre-release", '1.0.0-alpha.1', 1G, 0G, 0G, 'alpha.1', null),
                of("Alpha0.valid pre-release", '1.0.0-alpha0.valid', 1G, 0G, 0G, 'alpha0.valid', null),
                of("Alpha.0valid pre-release", '1.0.0-alpha.0valid', 1G, 0G, 0G, 'alpha.0valid', null),
                of("Complex pre-release with meta", '1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay', 1G, 0G, 0G, 'alpha-a.b-c-somethinglong', 'build.1-aef.1-its-okay'),
                of("Rc.1 with build", '1.0.0-rc.1+build.1', 1G, 0G, 0G, 'rc.1', 'build.1'),
                of("Rc.1 with build number", '2.0.0-rc.1+build.123', 2G, 0G, 0G, 'rc.1', 'build.123'),
                of("Beta with version", '1.2.3-beta', 1G, 2G, 3G, 'beta', null),
                of("Dev snapshot", '10.2.3-DEV-SNAPSHOT', 10G, 2G, 3G, 'DEV-SNAPSHOT', null),
                of("Snapshot with number", '1.2.3-SNAPSHOT-123', 1G, 2G, 3G, 'SNAPSHOT-123', null),
                of("Simple release", '1.0.0', 1G, 0G, 0G, null, null),
                of("Major release", '2.0.0', 2G, 0G, 0G, null, null),
                of("Minor release", '1.1.7', 1G, 1G, 7G, null, null),
                of("Build number", '2.0.0+build.1848', 2G, 0G, 0G, null, 'build.1848'),
                of("Alpha with number", '2.0.1-alpha.1227', 2G, 0G, 1G, 'alpha.1227', null),
                of("Alpha with beta meta", '1.0.0-alpha+beta', 1G, 0G, 0G, 'alpha', 'beta'),
                of("Complex RC snapshot", '1.2.3----RC-SNAPSHOT.12.9.1--.12+788', 1G, 2G, 3G, '---RC-SNAPSHOT.12.9.1--.12', '788'),
                of("Complex R-S with meta", '1.2.3----R-S.12.9.1--.12+meta', 1G, 2G, 3G, '---R-S.12.9.1--.12', 'meta'),
                of("Complex RC snapshot without meta", '1.2.3----RC-SNAPSHOT.12.9.1--.12', 1G, 2G, 3G, '---RC-SNAPSHOT.12.9.1--.12', null),
                of("Complex build meta", '1.0.0+0.build.1-rc.10000aaa-kk-0.1', 1G, 0G, 0G, null, '0.build.1-rc.10000aaa-kk-0.1'),
                of("Large numbers", '99999999999999999999999.999999999999999999.99999999999999999', 99999999999999999999999G, 999999999999999999G, 99999999999999999G, null, null),
                of("Legal alpha", '1.0.0-0A.is.legal', 1G, 0G, 0G, '0A.is.legal', null)
                )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    void "Should be able to parse semver"(String testName, String version, BigInteger major, BigInteger minor, BigInteger patch, String preRelease, String buildMetaData) {
        Semver semver = parse(version)
        assertNotNull(semver)
        assertEquals(major, semver.major)
        assertEquals(minor, semver.minor)
        assertEquals(patch, semver.patch)
        assertEquals(preRelease, semver.preRelease)
        assertEquals(buildMetaData, semver.buildMetaData)
        assertEquals(version, semver.version)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    void "Should be able to bump major"(String testName, String version, BigInteger major, BigInteger minor, BigInteger patch, String preRelease, String buildMetaData) {
        Semver bumped = parse(version).bumpMajor()
        assertNotNull(bumped)
        assertEquals(major + 1, bumped.major)
        assertEquals(0G, bumped.minor)
        assertEquals(0G, bumped.patch)
        assertEquals(preRelease, bumped.preRelease)
        assertEquals(buildMetaData, bumped.buildMetaData)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    void "Should be able to bump minor"(String testName, String version, BigInteger major, BigInteger minor, BigInteger patch, String preRelease, String buildMetaData) {
        Semver bumped = parse(version).bumpMinor()
        assertNotNull(bumped)
        assertEquals(major, bumped.major)
        assertEquals(minor + 1, bumped.minor)
        assertEquals(0G, bumped.patch)
        assertEquals(preRelease, bumped.preRelease)
        assertEquals(buildMetaData, bumped.buildMetaData)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    void "Should be able to bump patch"(String testName, String version, BigInteger major, BigInteger minor, BigInteger patch, String preRelease, String buildMetaData) {
        Semver bumped = parse(version).bumpPatch()
        assertNotNull(bumped)
        assertEquals(major, bumped.major)
        assertEquals(minor, bumped.minor)
        assertEquals(patch + 1, bumped.patch)
        assertEquals(preRelease, bumped.preRelease)
        assertEquals(buildMetaData, bumped.buildMetaData)
    }
}
