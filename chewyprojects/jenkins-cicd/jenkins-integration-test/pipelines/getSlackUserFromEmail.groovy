@Library('jenkins-cicd') _

pipeline {
    agent any
    stages {
        stage('Validate Email for Slack') {
            steps {
                script {
                    String prefix = 'realemail'
                    assert "@${prefix}" == cicd.getSlackUserFromEmail("${prefix}@chewy.com")
                }
            }
        }
    }
}
