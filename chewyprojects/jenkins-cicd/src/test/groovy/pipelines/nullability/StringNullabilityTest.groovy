package pipelines.nullability

import org.junit.jupiter.api.Test;

import static pipelines.nullability.StringNullability.assertNotBlank;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringNullabilityTest {
    private final String input = 'test string'
    private final Object additionalInfo = 'test info'

    @Test
    void "assertNotBlank returns the string when it's not null or blank"() {
        assert input == assertNotBlank(input)
    }

    @Test
    void "assertNotBlank returns the string when it's not null or blank with additionalInfo"() {
        assert input == assertNotBlank(input, additionalInfo)
    }

    @Test
    void "assertNotBlank throws AssertionError when string is null"() {
        assertThrows(AssertionError.class) { assertNotBlank(null) }
    }

    @Test
    void "assertNotBlank throws AssertionError when string is null with additionalInfo"() {
        assertThrows(AssertionError.class) { assertNotBlank(null, 'custom error message') }
    }

    @Test
    void "assertNotBlank throws AssertionError when string is empty"() {
        assertThrows(AssertionError.class) { assertNotBlank('') }
    }

    @Test
    void "assertNotBlank throws AssertionError when string is empty with additionalInfo"() {
        assertThrows(AssertionError.class) { assertNotBlank('', 'custom error message') }
    }

    @Test
    void "assertNotBlank throws AssertionError when string is blank"() {
        assertThrows(AssertionError.class) { assertNotBlank('   ') }
    }

    @Test
    void "assertNotBlank throws AssertionError when string is blank with additionalInfo"() {
        assertThrows(AssertionError.class) { assertNotBlank('   ', 'custom error message') }
    }

    @Test
    void "assertNotBlank error message contains additionalInfo when provided"() {
        String additionalInfo = 'custom error message'

        assert assertThrows(AssertionError.class) {
            assertNotBlank(null, additionalInfo)
        }.message.contains(additionalInfo)
    }
}
