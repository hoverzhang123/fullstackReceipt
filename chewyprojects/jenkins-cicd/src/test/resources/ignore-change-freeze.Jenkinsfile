import pipelines.module.ClosureModule

def cicdPipeline = cicd.init(
        projectName: 'ignore-change-freeze',
        assignmentGroup: 'RXP Search and API Team',
        appModule: 'app',
        snow_environment: true,
        vertical: 'hlth',
        namespace: 'rxp-lib-test',
        deploymentLowerEnvironments: [['dev', 'qat'], ['stg']],
        slackParams: [
                failureOnly: false
        ],
        respectChangeFreezesProd: false,
        respectChangeFreezesNonprod: false,
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
