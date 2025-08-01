def cicdPipeline = cicd.init(
        projectName: 'jenkins-cicd-tests',
        assignmentGroup: 'RXP Search and API Team',
        snow_environment: true,
        vertical: 'hlth',
        namespace: 'rxp-lib-test',
        deploymentLowerEnvironments: [['dev', 'qat'], ['stg']],
        modules: [
            [type: 'Makefile', makeFilePath: 'test/makefile/path.mk'],
        ],
        slackParams: [
                failureOnly: false
        ],
        respectChangeFreezesProd: true,
        respectChangeFreezesNonprod: true,
)

pipeline {
    agent {
        label 'amzlnx2'
    }
    tools {
        jdk 'openjdk-17.0.1'
        terraform 'terraform-1.0.2'
    }
    environment {
        ARTIFACTORY_USER = credentials('artifactory-username')
        ARTIFACTORY_PASSWORD = credentials('artifactory-password')
    }
    options {
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')
    }

    stages {
        stage('Default stages') {
            steps {
                script {
                  cicdPipeline.defaultStages()
                }
            }
        }
    }

    post {
        failure {
            script {
              cicdPipeline.reportBuildFailure()
            }
        }
    }
}
