import pipelines.module.ClosureModule

def cicdPipeline = cicd.init(
        projectName: 'feature-branch',
        assignmentGroup: 'RXP Search and API Team',
        // aptAppModule.name intentionally removed to trigger root project build
        aptAppModule: [namespace: 'pet-health'],
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
