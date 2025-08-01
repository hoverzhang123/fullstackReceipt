package pipelines.semver

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertNull
import static pipelines.semver.Semver.parse
import static org.junit.jupiter.params.provider.Arguments.of

class SemverBumpPreReleaseTest {
    static Stream<Arguments> data() {
        return Stream.of(
                of('0.0.4', null),
                of('1.2.3', null),
                of('10.20.30', null),
                of('1.1.2-prerelease+meta', null),
                of('1.1.2+meta', null),
                of('1.1.2+meta-valid', null),
                of('1.0.0-alpha', null),
                of('1.0.0-beta', null),
                of('1.0.0-alpha.beta', null),
                of('1.0.0-alpha.beta.1', 'alpha.beta.2'),
                of('1.0.0-alpha.1', 'alpha.2'),
                of('1.0.0-alpha0.valid', null),
                of('1.0.0-alpha.0valid', null),
                of('1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay', null),
                of('1.0.0-rc.1+build.1', 'rc.2'),
                of('2.0.0-rc.1+build.123', 'rc.2'),
                of('1.2.3-beta', null),
                of('10.2.3-DEV-SNAPSHOT', null),
                of('1.2.3-SNAPSHOT-123', null),
                of('1.0.0', null),
                of('2.0.0', null),
                of('1.1.7', null),
                of('2.0.0+build.1848', null),
                of('2.0.1-alpha.1227', 'alpha.1228'),
                of('1.0.0-alpha+beta', null),
                of('1.2.3----RC-SNAPSHOT.12.9.1--.12+788', '---RC-SNAPSHOT.13.9.1--.12'),
                of('1.2.3----R-S.12.9.1--.12+meta', '---R-S.13.9.1--.12'),
                of('1.2.3----RC-SNAPSHOT.12.9.1--.12', '---RC-SNAPSHOT.13.9.1--.12'),
                of('1.0.0+0.build.1-rc.10000aaa-kk-0.1', null),
                of('99999999999999999999999.999999999999999999.99999999999999999', null),
                of('1.0.0-0A.is.legal', null)
                )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    void "Should be able to parse semver"(String version, String expectedPreRelease) {
        Semver semver = parse(version)
        assertNotNull(semver)

        Semver bumped = semver.bumpPreRelease()

        if (expectedPreRelease == null) {
            assertNull(bumped)
        } else {
            assertEquals(semver.setPreRelease(expectedPreRelease).version, bumped.version)
        }
    }
}
