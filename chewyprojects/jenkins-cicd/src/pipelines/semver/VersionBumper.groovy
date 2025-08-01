package pipelines.semver

/**
 * Utility class for determining semantic version bumps based on commit message or PR title analysis.
 */
class VersionBumper {

    private static final String MAJOR_PATTERN = /(?i)\[major\]/
    private static final String MINOR_PATTERN = /(?i)\[minor\]/
    private static final String PATCH_PATTERN = /(?i)\[patch\]/

    /**
     * Checks if the input string indicates a major version bump is needed.
     *
     * @param input The string to check, typically a commit message or PR title
     * @return true if the input contains the [Major] tag (case-insensitive), false otherwise
     */
    static boolean shouldBumpMajor(String input) {
        return input != null && input =~ MAJOR_PATTERN
    }

    /**
     * Checks if the input string indicates a minor version bump is needed.
     *
     * @param input The string to check, typically a commit message or PR title
     * @return true if the input contains the [Minor] tag (case-insensitive), false otherwise
     */
    static boolean shouldBumpMinor(String input) {
        return input != null && input =~ MINOR_PATTERN
    }

    /**
     * Checks if the input string indicates a patch version bump is needed.
     *
     * @param input The string to check, typically a commit message or PR title
     * @return true if the input contains the [Patch] tag (case-insensitive), false otherwise
     */
    static boolean shouldBumpPatch(String input) {
        return input != null && input =~ PATCH_PATTERN
    }

    static Semver autoBump(Semver semver, String message, String versionIncrement) {
        if (semver == null) {
            return  null
        }

        def bumpedVersion = bumpByMessage(semver, message)
        if (bumpedVersion != null) {
            return bumpedVersion
        }

        if (versionIncrement?.contains('major')) {
            return semver.bumpMajor()
        } else if (versionIncrement?.contains('patch')) {
            return semver.bumpPatch()
        } else if (versionIncrement?.contains('prerelease')) {
            return semver.bumpPreRelease()
        }

        return semver.bumpMinor()
    }

    private static Semver bumpByMessage(Semver semver, String message) {
        if (shouldBumpMajor(message)) {
            return semver.bumpMajor()
        } else if (shouldBumpMinor(message)) {
            return semver.bumpMinor()
        } else if (shouldBumpPatch(message)) {
            return semver.bumpPatch()
        }

        return null
    }
}
