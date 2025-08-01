// groovylint-disable LineLength, DuplicateNumberLiteral
package pipelines.semver

import java.util.regex.Matcher

class Semver implements Comparable<Semver> {
    /**
     * @see <a href="https://semver.org/spec/v2.0.0.html#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string">Semver Regex</a>
     */
    private static final String SEMVER_REGEX = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/
    public static final String DOT_REGEX = '\\.'
    public static final String DIGIT_REGEX = '\\d+'

    final BigInteger major
    final BigInteger minor
    final BigInteger patch
    final String preRelease
    final String buildMetaData

    private Semver(BigInteger major, BigInteger minor, BigInteger patch, String preRelease, String buildMetaData) {
        this.major = major
        this.minor = minor
        this.patch = patch
        this.preRelease = preRelease
        this.buildMetaData = buildMetaData
    }

    static Semver parse(String version) {
        Matcher matcher = version =~ SEMVER_REGEX

        if (matcher.matches()) {
            return new Semver(
                    matcher.group(1).toBigInteger(),
                    matcher.group(2).toBigInteger(),
                    matcher.group(3).toBigInteger(),
                    matcher.group(4),
                    matcher.group(5),
                    )
        }

        return null
    }

    String getVersion() {
        return "${major}.${minor}.${patch}" +
                (preRelease ? "-$preRelease" : '') +
                (buildMetaData ? "+$buildMetaData" : '')
    }

    Semver bumpMajor() {
        return new Semver(major + 1, minor, patch, preRelease, buildMetaData).setMinor().setPatch()
    }

    Semver bumpMinor() {
        return new Semver(major, minor + 1, patch, preRelease, buildMetaData).setPatch()
    }

    Semver bumpPatch() {
        return new Semver(major, minor, patch + 1, preRelease, buildMetaData)
    }

    Semver bumpPreRelease() {
        String updatedPreRelease = bumpFirstNumber(preRelease)

        return updatedPreRelease ? new Semver(major, minor, patch, updatedPreRelease, buildMetaData) : null
    }

    Semver setMinor(BigInteger newMinor = 0G) {
        return new Semver(major, newMinor, patch, preRelease, buildMetaData)
    }

    Semver setPatch(BigInteger newPatch = 0G) {
        return new Semver(major, minor, newPatch, preRelease, buildMetaData)
    }

    Semver setPreRelease(String preRelease = null) {
        return new Semver(major, minor, patch, preRelease, buildMetaData)
    }

    Semver setBuildMetaData(String buildMetaData = null) {
        return new Semver(major, minor, patch, preRelease, buildMetaData)
    }

    Boolean isRelease() {
        return (preRelease == null || preRelease.empty) && (buildMetaData == null || buildMetaData.empty)
    }

    @Override
    int compareTo(Semver o) {
        int result = major <=> o.major
        if (result != 0) {
            return result
        }

        result = minor <=> o.minor
        if (result != 0) {
            return result
        }

        result = patch <=> o.patch
        if (result != 0) {
            return result
        }

        if (preRelease == null && o.preRelease == null) {
            return 0
        }
        if (preRelease == null) {
            return 1
        }
        if (o.preRelease == null) {
            return -1
        }

        String[] thisParts = preRelease.split(DOT_REGEX)
        String[] otherParts = o.preRelease.split(DOT_REGEX)

        int minLength = Math.min(thisParts.length, otherParts.length)

        for (int i = 0; i < minLength; i++) {
            String thisPart = thisParts[i]
            String otherPart = otherParts[i]

            boolean isNumeric = thisPart.matches(DIGIT_REGEX)
            boolean isOtherNumeric = otherPart.matches(DIGIT_REGEX)

            if (isNumeric && isOtherNumeric) {
                result = new BigInteger(thisPart) <=> new BigInteger(otherPart)
                if (result != 0) {
                    return result
                }
            } else if (isNumeric) {
                return -1
            } else if (isOtherNumeric) {
                return 1
            } else {
                result = thisPart <=> otherPart
                if (result != 0) {
                    return result
                }
            }
        }

        return Integer.compare(thisParts.length, otherParts.length)
    }

    private static String bumpFirstNumber(String preRelease) {
        if (preRelease != null) {
            String[] parts = preRelease.split(DOT_REGEX)

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].matches(DIGIT_REGEX)) {
                    parts[i] = parts[i].toBigInteger() + 1
                    return parts.join('.')
                }
            }
        }

        return null
    }
}
