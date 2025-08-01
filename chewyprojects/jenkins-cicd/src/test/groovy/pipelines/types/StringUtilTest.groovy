package pipelines.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.params.provider.Arguments.of
import static pipelines.types.StringUtil.trimNonNumericPrefix

class StringUtilTest {

    static Stream<Arguments> trimNonNumericPrefixData() {
        return Stream.of(
                of("Version with 'v' prefix", 'v1.2.3', '1.2.3'),
                of("Version with 'V' prefix", 'V2.0.0', '2.0.0'),
                of("Version with 'version' prefix", 'version1.0.0', '1.0.0'),
                of("Version with multiple letter prefix", 'release1.2.3', '1.2.3'),
                of("Version with dash prefix", 'r-1.2.3', '1.2.3'),
                of("Version with underscore prefix", 'v_1.2.3', '1.2.3'),
                of("Version with space prefix", ' 1.2.3', '1.2.3'),
                of("Version with mixed prefix", 'rel-v_1.2.3', '1.2.3'),
                of("Version already numeric", '1.2.3', '1.2.3'),
                of("Version starting with zero", '0.1.0', '0.1.0'),
                of("Empty string", '', ''),
                of("Only non-numeric", 'version', ''),
                of("Complex prefix", 'MyApp-v2.1.0-beta', '2.1.0-beta'),
                of("Prefix with symbols", '@#$%1.0.0', '1.0.0')
                )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trimNonNumericPrefixData")
    void "should trim non-numeric prefix correctly"(String testName, String input, String expected) {
        assertEquals(expected, trimNonNumericPrefix(input))
    }

    @Test
    void "should handle null input gracefully"() {
        assert trimNonNumericPrefix(null) == null
    }

    @Test
    void "should preserve version with only digits and dots"() {
        String version = '1.2.3.4.5'
        assertEquals(version, trimNonNumericPrefix(version))
    }

    @Test
    void "should handle version with build metadata"() {
        String input = 'v1.0.0+build.123'
        String expected = '1.0.0+build.123'
        assertEquals(expected, trimNonNumericPrefix(input))
    }

    @Test
    void "should handle version with prerelease"() {
        String input = 'version2.0.0-alpha.1'
        String expected = '2.0.0-alpha.1'
        assertEquals(expected, trimNonNumericPrefix(input))
    }
}
