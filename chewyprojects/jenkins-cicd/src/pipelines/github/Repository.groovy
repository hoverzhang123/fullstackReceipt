package pipelines.github

import static pipelines.github.Api.get
import static pipelines.github.Api.toJson
import static pipelines.github.Auth.withGithubAuth
import static pipelines.github.Repository.getDefaultBranch
import static pipelines.jenkins.Environment.getBranchName
import static pipelines.jenkins.Environment.getChangeBranch
import static pipelines.jenkins.Environment.getChangeUrl
import static pipelines.jenkins.Environment.getGitBranch
import static pipelines.jenkins.Environment.getGitUrl
import static pipelines.jenkins.Environment.getGithubToken
import static pipelines.nullability.StringNullability.assertNotBlank
import static pipelines.types.StringUtil.trimNonNumericPrefix

import pipelines.github.api.GitTag
import pipelines.semver.Semver


class Repository {
    private static final String FORWARD_SLASH = '/'
    private static final String ORIGIN_PREFIX = 'origin/'

    static String getDefaultBranch(String owner, String repo, String token) {
        guardInput(owner, repo, token)

        return toJson(get(prependRepositoryUrl(owner, repo), token))?.default_branch
    }

    /**
     * Extracts the owner name from a list of candidate GitHub URLs.
     *
     * @param candidateUrls A list of potential GitHub repository URLs
     * @return The GitHub repository owner name, or null if it cannot be determined
     */
    static String getOwner(List<String> candidateUrls) {
        List<String> parts = candidateUrls
                ?.collect { url -> toPath(url) }
                ?.find { url -> url != null }
                ?.tokenize(FORWARD_SLASH)

        return parts != null && parts.size() >= 1 ? parts.first() : null
    }

    /**
     * Extracts the repository name from a list of candidate GitHub URLs.
     *
     * @param candidateUrls A list of potential GitHub repository URLs
     * @return The GitHub repository name (without .git extension), or null if it cannot be determined
     */
    static String getRepository(List<String> candidateUrls) {
        List<String> parts = candidateUrls
                ?.collect { url -> toPath(url) }
                ?.find { url -> url != null }
                ?.tokenize(FORWARD_SLASH)

        return parts?.size() >= 2 ? parts[1]?.replace('.git', '') : null
    }

    /**
     * Determines if the current branch is the default branch in the GitHub repository.
     *
     * @param jenkins The Jenkins pipeline script object for executing shell commands
     * @param env The Jenkins environment object
     * @return true if the current branch is the default branch, false otherwise
     */
    static Boolean isDefaultBranch(Script jenkins) {
        withGithubAuth(jenkins) {
            String gitBranch = jenkins.sh(returnStdout: true, script: "git rev-parse --abbrev-ref HEAD").trim()

            List<String> candidates = [
                gitBranch,
                getGitBranch(jenkins.env),
                getBranchName(jenkins.env),
                getChangeBranch(jenkins.env),
            ]
            .findAll { it != null }
            .findAll { !it.blank }
            .collect { it.startsWith(ORIGIN_PREFIX) ? it.substring(ORIGIN_PREFIX.length()) : it }

            String defaultBranch = getDefaultBranch(getGithubOwner(jenkins.env), getGithubRepository(jenkins.env), getGithubToken(jenkins.env))

            return candidates.any { it == defaultBranch }
        }
    }

    static String getLatestTag(String owner, String repo, String token) {
        List<GitTag> tags = listTags(owner, repo, token)

        return tags.isEmpty() ? null : tags.first().name
    }

    static GitTag getLatestReleaseTag(String owner, String repo, String token) {
        return maxRelease(listTags(owner, repo, token))
    }

    static List<GitTag> listTags(String owner, String repo, String token) {
        guardInput(owner, repo, token)

        try {
            String apiUrl = "https://api.github.com/repos/${owner}/${repo}/git/refs/tags"
            List response = toJson(get(apiUrl, token)) as ArrayList

            List<GitTag> tags = response.collect { tag ->
                def sha = tag.get('object').get('sha')
                new GitTag(tag.get('ref').replace('refs/tags/', ''), sha?.toString())
            }.toList()

            return tags.findAll { it.sha != null && it.sha != 'null' }
        } catch (Exception ignore) {
            return []
        }
    }

    static void guardInput(String owner, String repo, String token) {
        assertNotBlank(owner, 'Owner (eg Chewy-Int, Chewy-Inc)')
        assertNotBlank(repo, 'Repo (e.g., your GitHub project name)')
        assertNotBlank(token, 'Github Token')
    }

    private static String toPath(String url) {
        if(url?.empty) {
            return null
        }

        try {
            return new URI(url).toURL().path
        } catch (URISyntaxException ignored) {
        } catch (MalformedURLException ignored) {
        }

        return null
    }

    private static String prependRepositoryUrl(String owner, String repo) {
        return "https://api.github.com/repos/${owner}/${repo}"
    }

    private static String getGithubOwner(Object env) {
        return getOwner([
            getGitUrl(env),
            getChangeUrl(env),
        ])
    }

    private static String getGithubRepository(Object env) {
        return getRepository([
            getGitUrl(env),
            getChangeUrl(env),
        ])
    }

    private static GitTag maxRelease(List<GitTag> tags) {
        return tags.findAll {
            Semver.parse(trimNonNumericPrefix(it?.name ?: ''))?.release == true
        }.max {
            Semver.parse(trimNonNumericPrefix(it.name))
        }
    }
}
