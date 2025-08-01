package pipelines.semver


import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.params.provider.Arguments.of
import static pipelines.semver.Semver.parse

class SemverParserInvalidTest {
    static Stream<Arguments> data() {
        return Stream.of(
                of('1'),
                of('1.2'),
                of('1.2.3-0123'),
                of('1.2.3-0123.0123'),
                of('1.1.2+.123'),
                of('+invalid'),
                of('-invalid'),
                of('-invalid+invalid'),
                of('-invalid.01'),
                of('alpha'),
                of('alpha.beta'),
                of('alpha.beta.1'),
                of('alpha.1'),
                of('alpha+beta'),
                of('alpha_beta'),
                of('alpha.'),
                of('alpha..'),
                of('beta'),
                of('1.0.0-alpha_beta'),
                of('-alpha.'),
                of('1.0.0-alpha..'),
                of('1.0.0-alpha..1'),
                of('1.0.0-alpha...1'),
                of('1.0.0-alpha....1'),
                of('1.0.0-alpha.....1'),
                of('1.0.0-alpha......1'),
                of('1.0.0-alpha.......1'),
                of('01.1.1'),
                of('1.01.1'),
                of('1.1.01'),
                of('1.2'),
                of('1.2.3.DEV'),
                of('1.2-SNAPSHOT'),
                of('1.2.31.2.3----RC-SNAPSHOT.12.09.1--..12+788'),
                of('1.2-RC-SNAPSHOT'),
                of('-1.0.3-gamma+b7718'),
                of('+justmeta'),
                of('9.8.7+meta+meta'),
                of('9.8.7-whatever+meta+meta'),
                of('99999999999999999999999.999999999999999999.99999999999999999----RC-SNAPSHOT.12.09.1--------------------------------..12'),
                of('1.0.0-0A.is.legal')
                )
    }


    @MethodSource("data")
    void "Should not be able to parse invalid semver"(String version) {
        def exception = assertThrows(IllegalArgumentException.class) {
            parse(version)
        }
        assertTrue(exception.message.contains("Invalid version"))
    }
}
