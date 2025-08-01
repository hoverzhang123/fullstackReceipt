package pipelines.helpers


class SemverHelper {
    private final String defaultVersion

    SemverHelper(String defaultVersion = "1.2.3") {
        this.defaultVersion = defaultVersion
    }

    String getPreReleaseVersion(String positionPreference) {
        return "${defaultVersion}-snapshot"
    }

    String getVersion(String positionPreference) {
        return defaultVersion
    }
}
