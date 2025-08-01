package pipelines.semver

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.params.provider.Arguments.of
import static pipelines.semver.Semver.parse
import static pipelines.semver.SemverCompareToTest.ExpectedResult.NEGATIVE
import static pipelines.semver.SemverCompareToTest.ExpectedResult.POSITIVE
import static pipelines.semver.SemverCompareToTest.ExpectedResult.ZERO

class SemverCompareToTest {

    static Stream<Arguments> data() {
        return Stream.of(
                of("Equal versions", ZERO, '1.0.0', '1.0.0'),
                of("Equal alpha versions", ZERO, '1.0.0-alpha.1', '1.0.0-alpha.1'),
                of("Equal alpha.beta versions", ZERO, '1.0.0-alpha.beta', '1.0.0-alpha.beta'),
                of("Equal alpha versions", ZERO, '1.0.0-alpha', '1.0.0-alpha'),
                of("Equal beta.11 versions", ZERO, '1.0.0-beta.11', '1.0.0-beta.11'),
                of("Equal beta.2 versions", ZERO, '1.0.0-beta.2', '1.0.0-beta.2'),
                of("Equal beta versions", ZERO, '1.0.0-beta', '1.0.0-beta'),
                of("Equal rc.1 versions", ZERO, '1.0.0-rc.1', '1.0.0-rc.1'),
                of("Equal release versions", ZERO, '1.0.0', '1.0.0'),
                of("Equal 2.0.0 versions", ZERO, '2.0.0', '2.0.0'),
                of("Equal 2.1.0 versions", ZERO, '2.1.0', '2.1.0'),
                of("Equal 2.1.1 versions", ZERO, '2.1.1', '2.1.1'),
                of("Major version less than", NEGATIVE, '1.0.0', '2.0.0'),
                of("Minor version less than", NEGATIVE, '2.0.0', '2.1.0'),
                of("Patch version less than", NEGATIVE, '2.1.0', '2.1.1'),
                of("Alpha less than alpha.1", NEGATIVE, '1.0.0-alpha', '1.0.0-alpha.1'),
                of("Alpha.1 less than alpha.beta", NEGATIVE, '1.0.0-alpha.1', '1.0.0-alpha.beta'),
                of("Alpha.beta less than beta", NEGATIVE, '1.0.0-alpha.beta', '1.0.0-beta'),
                of("Beta less than beta.2", NEGATIVE, '1.0.0-beta', '1.0.0-beta.2'),
                of("Beta.2 less than beta.11", NEGATIVE, '1.0.0-beta.2', '1.0.0-beta.11'),
                of("Beta.11 less than rc.1", NEGATIVE, '1.0.0-beta.11', '1.0.0-rc.1'),
                of("Rc.1 less than release", NEGATIVE, '1.0.0-rc.1', '1.0.0'),
                of("Major version greater than", POSITIVE, '2.0.0', '1.0.0'),
                of("Minor version greater than", POSITIVE, '2.1.0', '2.0.0'),
                of("Patch version greater than", POSITIVE, '2.1.1', '2.1.0'),
                of("Alpha.1 greater than alpha", POSITIVE, '1.0.0-alpha.1', '1.0.0-alpha'),
                of("Alpha.beta greater than alpha.1", POSITIVE, '1.0.0-alpha.beta', '1.0.0-alpha.1'),
                of("Beta greater than alpha.beta", POSITIVE, '1.0.0-beta', '1.0.0-alpha.beta'),
                of("Beta.2 greater than beta", POSITIVE, '1.0.0-beta.2', '1.0.0-beta'),
                of("Beta.11 greater than beta.2", POSITIVE, '1.0.0-beta.11', '1.0.0-beta.2'),
                of("Rc.1 greater than beta.11", POSITIVE, '1.0.0-rc.1', '1.0.0-beta.11'),
                of("Release greater than rc.1", POSITIVE, '1.0.0', '1.0.0-rc.1')
                )
    }

    static enum ExpectedResult {
        POSITIVE,
        NEGATIVE,
        ZERO
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    void "Semver compareTo"(String testName, ExpectedResult expected, String leftVersion, String rightVersion) {
        Semver leftSemver = parse(leftVersion)
        Semver rightSemver = parse(rightVersion)

        assertNotNull(leftSemver)
        assertNotNull(rightSemver)

        assertEquals(expected, toExpectedResult(leftSemver <=> rightSemver))
    }

    private static ExpectedResult toExpectedResult(int input) {
        if (input > 0) {
            return POSITIVE
        } else if (input < 0) {
            return NEGATIVE
        }
        return ZERO
    }
}
