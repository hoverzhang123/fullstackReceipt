import static pipelines.git.Commit.getHeadMessage
import static pipelines.git.Commit.getHeadSha
import static pipelines.github.Repository.getOwner
import static pipelines.github.Repository.getRepository
import static pipelines.github.Repository.getLatestReleaseTag
import static pipelines.jenkins.Environment.getGithubToken
import static pipelines.jenkins.Environment.getGitUrl
import static pipelines.jenkins.Environment.getChangeUrl
import static pipelines.semver.VersionBumper.autoBump
import static pipelines.types.StringUtil.trimNonNumericPrefix

import pipelines.github.api.GitTag
import pipelines.semver.Semver

/**
 * Parses a semantic version string into a Semver object.
 *
 * @param semver The semantic version string eg 1.2.3-prerelease+buildMetaData
 * @return A {@link Semver} object representing the parsed version, null if invalid
 */
Semver parse(String semver) {
    return Semver.parse(semver)
}

/**
 * Parses a semantic version string into a Semver object.
 *
 * @param semver The semantic version string eg 1.2.3-prerelease+buildMetaData
 * @return A {@link Semver} object representing the parsed version, null if invalid
 * @throws AssertionError if version fails to parse
 */
Semver parseRequired(String semver) {
    Semver s = Semver.parse(semver)
    assert s != null : "Unable to parse semver: ${semver}, please see https://semver.org/spec/v2.0.0.html"
    return s
}

/**
 * Compares two semantic version strings.
 *
 * @param left  The first semantic version string to compare
 * @param right The second semantic version string to compare
 * @return A negative, zero, or positive integer; left version is less than, equal to, or greater than the right
 * @throws AssertionError if either version string fails to parse
 */
int compare(String left, String right) {
    return parseRequired(left) <=> parseRequired(right)
}

/**
 * Increments the major version number of a semantic version string.
 *
 * @param semver The semantic version string to bump
 * @return A new version string with the major version incremented, null if invalid semver
 */
String bumpMajor(String semver) {
    return parseRequired(semver).bumpMajor().version
}

/**
 * Increments the minor version number of a semantic version string.
 *
 * @param semver The semantic version string to bump
 * @return A new version string with the minor version incremented, null if invalid
 */
String bumpMinor(String semver) {
    return parseRequired(semver).bumpMinor().version
}

/**
 * Increments the patch version number of a semantic version string.
 *
 * @param semver The semantic version string to bump
 * @return A new version string with the patch version incremented, null if invalid
 */
String bumpPatch(String semver) {
    return parseRequired(semver).bumpPatch().version
}

/**
 * Increments the first number in preRelease (split on `.`)
 *
 * @param semver The semantic version string to bump
 * @return A new version string with the preRelease version incremented, null if invalid
 */
String bumpPreRelease(String semver) {
    Semver s = parseRequired(semver)
    Semver bumped = s.bumpPreRelease()

    assert bumped : "Unable to bump preRelease: ${s.preRelease};" +
    ' an isolated number, or separated by `.` is required to auto bump preRelease'

    return bumped.version
}

String getVersion(
        String versionIncrement = null,
        String githubOwner = getGithubOwner(this),
        String githubRepo = getGithubRepository(this),
        String githubToken = getGithubToken(env)
) {
    GitTag tag = getLatestReleaseTag(githubOwner, githubRepo, githubToken)

    echo("Latest tag: ${tag?.name ?: 'No tags found'} ")

    if (tag) {
        Semver semver = parse(trimNonNumericPrefix(tag.name))?.with {
            autoBump(it, getHeadMessage(this), versionIncrement)
        }

        if (semver != null) {
            return semver.version
        }
    }

    return '1.0.0'
}

String getPreReleaseVersion(
        String versionIncrement = null,
        String githubOwner = getGithubOwner(this),
        String githubRepo = getGithubRepository(this),
        String githubToken = getGithubToken(env)
) {
    return parse(getVersion(versionIncrement, githubOwner, githubRepo, githubToken))
            .setPreRelease("dev.${getHeadSha(this).take(7)}")
            .version
}

private String getGithubOwner(Script jenkins) {
    return getOwner([
        getGitUrl(jenkins.env),
        getChangeUrl(jenkins.env),
    ])
}

private String getGithubRepository(Script jenkins) {
    return getRepository([
        getGitUrl(jenkins.env),
        getChangeUrl(jenkins.env),
    ])
}
