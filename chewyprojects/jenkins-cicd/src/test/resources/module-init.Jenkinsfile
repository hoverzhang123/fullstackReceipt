import pipelines.module.E2EModule

def moduleImpls = [
    'build': {echo 'Running build'},
    'validate': {echo 'Running validate'},
    'publish': {echo 'Running publish'},
    'preCheck': {echo 'Running preCheck'},
    'regionDeploy': {echo 'Running regionDeploy'},
    'postCheck': {echo 'Running postCheck'},
    'regionRollback': {echo 'Running regionRollback'}
]

def cicdPipeline = cicd.init(
        projectName: 'jenkins-cicd-tests',
        assignmentGroup: 'RXP Search and API Team',
        snow_environment: true,
        vertical: 'hlth',
        namespace: 'rxp-lib-test',
        deploymentLowerEnvironments: [['dev', 'qat'], ['stg']],
        modules: [
            [type: 'App', name: 'app'],
            [type: 'Helm'],
            [type: 'Datadog'],
            [type: 'Docker', name: 'aptApp', moduleName: 'AptDockerModule'],
            [type: 'Dynatrace', enableDeployObservability: true],
            [type: 'AptHelm', namespace: 'pet-health'],
            [type: 'Datadog'],
            [type: 'Terraform'],
            [type: 'Flyway'],
            [type: E2EModule.class],
            [type: 'Library', names: ['library', 'library2']],
            [type: 'Spec'],
            [type: 'Closure', name: 'test-closure-module', closureMap: moduleImpls],
            [type: 'Lambda', name: 'lambda-module-test'],
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
