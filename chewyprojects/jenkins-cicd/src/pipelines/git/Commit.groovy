package pipelines.git

class Commit {
    static String getHeadMessage(Script script) {
        return script.sh(returnStdout: true, script: 'git --no-pager log --format=%B -n 1 HEAD').trim()
    }

    static String getHeadSha(Script script) {
        return script.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    }
}
