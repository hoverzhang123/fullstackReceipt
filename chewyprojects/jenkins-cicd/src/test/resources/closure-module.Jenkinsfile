import pipelines.module.ClosureModule
import pipelines.module.LambdaModule

def moduleImpls = [
    'build': { echo 'Running build' },
    'validate': { echo 'Running validate' },
    'publish': { echo 'Running publish' },
    'preCheck': { echo 'Running preCheck' },
    'regionDeploy': { echo 'Running regionDeploy' },
    'postCheck': { echo 'Running postCheck' },
    'regionRollback': { echo 'Running regionRollback' }
]

def customClosureModule = ClosureModule.builder()
    .name('test-closure-module')
    .closureMap(moduleImpls)
    .dockerImage('278833423079.dkr.ecr.us-east-1.amazonaws.com/hlth/rxp-docker-jdk:1.0.0-jdk17')
    .envs(['dev'])
    .build()

def lambdaModule = new LambdaModule('lambda-module-test')

def defaultStageEcho = {
    echo 'Running init stage for defaultStages'
}

def cicdPipeline = cicd.init(
        projectName: 'closure-module',
        assignmentGroup: 'RXP Search and API Team',
        appModule: 'app',
        aptAppModule: [name: 'aptApp', namespace: 'pet-health'],
        flywayModule: 'flyway',
        terraformModule: 'terraform',
        e2eModule: 'e2e',
        libraryModule: ['library', 'library2'],
        snow_environment: true,
        vertical: 'hlth',
        namespace: 'rxp-lib-test',
        deploymentLowerEnvironments: [['dev', 'qat'], ['stg']],
        modules: [customClosureModule, lambdaModule],
        slackParams: [
                failureOnly: false
        ],
        respectChangeFreezesProd: true,
        respectChangeFreezesNonprod: true,
        uninstall: true,
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
                  cicdPipeline.defaultStages(defaultStageEcho)
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
