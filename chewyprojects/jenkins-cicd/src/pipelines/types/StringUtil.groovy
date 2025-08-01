package pipelines.types

import java.util.regex.Pattern

class StringUtil {
    private static final Pattern NON_NUMERIC_PREFIX = ~/^[^\d]*/

    static String trimNonNumericPrefix(String version) {
        if (version == null) {
            return null
        }
        return version.replaceFirst(NON_NUMERIC_PREFIX, '')
    }
}
