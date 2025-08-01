package pipelines.github

class Auth {
    static Object withGithubAuth(Script script, Closure block) {
        script.withCredentials([
            script.usernamePassword(credentialsId: 'jenkins-github-userpass', passwordVariable: 'GH_TOKEN', usernameVariable: 'GRGIT_USER')
        ]) {
            return block.call()
        }
    }
}
