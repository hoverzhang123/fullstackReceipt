package pipelines.github.api

class GitTag {
    final String name
    final String sha

    GitTag(String name, String sha) {
        this.name = name
        this.sha = sha
    }
}
