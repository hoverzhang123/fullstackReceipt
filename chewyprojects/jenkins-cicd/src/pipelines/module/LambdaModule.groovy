package pipelines.module

import pipelines.PipelineConfig
import pipelines.module.stage.SonarqubeStage
import pipelines.module.stage.PublishDockerStage
import pipelines.metadata.CommonUtil

import java.util.concurrent.TimeoutException

class LambdaModule extends Module {
    Map<String, String> dockerPrefixLambdaNameMap
    LambdaModule(String name, Map<String, String> dockerPrefixLambdaNameMap = null, String ecrRepo = null) {
        super([name: name, type: 'lambda', ecrRepo: ecrRepo])
        this.dockerPrefixLambdaNameMap = dockerPrefixLambdaNameMap ?: ['docker' : 'lambda']
    }

    LambdaModule(PipelineConfig config, props) {
        super(props)
        this.dockerPrefixLambdaNameMap = props.dockerPrefixLambdaNameMap ?: ['docker' : 'lambda']
    }

    def buildLambda(ModuleConfig config) {
        def jenkins = config.jenkins
        jenkins.env.DEPLOY_STEP = "Build Lambda"

        for (String dockerPrefix : dockerPrefixLambdaNameMap.keySet()) {
            Integer waitSeconds = 2
            boolean success = false
            while (waitSeconds <= 16) {
                try {
                    String gradleCommand = "./gradlew -PprojectVersion=${config.projectVersion} " +
                            "${name}:${dockerPrefix}BuildImage -x test"
                    jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS['shd']}:role/CHEWY-cross-jenkins") {
                        jenkins.sh """#!/bin/bash
                            ${jenkins.ecrLogin()}
                            ${gradleCommand}
                        """
                    }
                    success = true
                    break
                } catch(Exception e) {
                    jenkins.echo("LAMBDA BUILD EXCEPTION")
                    jenkins.echo((String) e.getMessage())
                    jenkins.echo("retrying after ${waitSeconds} seconds")
                    Thread.sleep(waitSeconds * 1000)
                    waitSeconds = waitSeconds * 2
                }
            }
            if (!success) {
                throw new TimeoutException("Failed to build lambda")
            }
        }
    }

    def publishLambda(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def previousVersion = config.previousVersion
        def vertical = config.vertical
        jenkins.env.DEPLOY_STEP = "Publish Lambda - ${projectVersion}"

        def image = CommonUtil.getImageBaseUrl(jenkins, props.ecrRepo, vertical, projectName)
        jenkins.sh "${jenkins.ecrLogin()}"
        for (String lambdaName : dockerPrefixLambdaNameMap.values()) {
            PublishDockerStage.publishImage(jenkins, image, lambdaName, projectVersion, previousVersion)
        }
    }

    def deployLambda(ModuleConfig config) {
        def jenkins = config.jenkins
        String projectName = config.projectName
        String projectPrefix = projectName.replace("-service", "")
        String projectVersion = config.projectVersion
        String vertical = config.vertical
        String env = config.env
        String region = config.region
        jenkins.env.DEPLOY_STEP = "Deploy Lambda"

        jenkins.echo("Attempt to Deploy Lambda with version: ${projectVersion} for env: ${env} region: ${region}")

        jenkins.withAWS(role:"arn:aws:iam::${jenkins.ACCOUNTS['shd']}:role/CHEWY-cross-jenkins") {
            jenkins.sh """#!/bin/bash
            aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${jenkins.ACCOUNTS['shd']}.dkr.ecr.us-east-1.amazonaws.com
        """
        }
        for (String lambdaName : dockerPrefixLambdaNameMap.values()) {
            Integer waitSeconds = 2
            boolean success = false
            while (waitSeconds <= 16) {
                try {
                    String lambdaFunctionName = "${projectPrefix}-${lambdaName}"
                    def image = CommonUtil.getImageBaseUrl(jenkins, props.ecrRepo, vertical, projectName, region)
                    String imageURI = "${image}:${lambdaName}-${projectVersion}"
                    jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS[env]}:role/CHEWY-cross-jenkins") {
                        jenkins.sh """#!/bin/bash
                        docker run \
                        -e AWS_ACCESS_KEY_ID \
                        -e AWS_SECRET_ACCESS_KEY \
                        -e AWS_SESSION_TOKEN \
                        ${jenkins.ACCOUNTS['shd']}.dkr.ecr.us-east-1.amazonaws.com/ecr-public/aws-cli/aws-cli:latest \
                        lambda update-function-code \
                        --region ${region} \
                        --function-name ${lambdaFunctionName} \
                        --image-uri "${imageURI}"
                        """
                    }
                    success = true
                    break
                } catch(Exception e) {
                    jenkins.echo("LAMBDA DEPLOY EXCEPTION")
                    jenkins.echo((String) e.getMessage())
                    jenkins.echo("retrying after ${waitSeconds} seconds")
                    Thread.sleep(waitSeconds * 1000)
                    waitSeconds = waitSeconds * 2
                }
            }
            if (!success) {
                throw new TimeoutException("Failed to deploy lambda")
            }
        }
    }

    def rollbackLambda(ModuleConfig config) {
        def jenkins = config.jenkins
        String projectName = config.projectName
        String projectPrefix = projectName.replace("-service", "")
        String previousVersion = config.previousVersion
        String vertical = config.vertical
        String env = config.env
        String region = config.region
        jenkins.env.DEPLOY_STEP = "Rollback Lambda: ${previousVersion}"

        jenkins.echo("Attempt to Rollback with version: ${previousVersion} for env: ${env} region: ${region}")

        try {
            jenkins.withAWS(role:"arn:aws:iam::${jenkins.ACCOUNTS['shd']}:role/CHEWY-cross-jenkins") {
                jenkins.sh """#!/bin/bash
            aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${jenkins.ACCOUNTS['shd']}.dkr.ecr.us-east-1.amazonaws.com
        """
            }
            for (String lambdaName : dockerPrefixLambdaNameMap.values()) {
                String lambdaFunctionName = "${projectPrefix}-${lambdaName}"
                def image = CommonUtil.getImageBaseUrl(jenkins, props.ecrRepo, vertical, projectName, region)
                String imageURI = "${image}:${lambdaName}-${previousVersion}"
                jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS[env]}:role/CHEWY-cross-jenkins") {
                    jenkins.sh """#!/bin/bash
                docker run \
                    -e AWS_ACCESS_KEY_ID \
                    -e AWS_SECRET_ACCESS_KEY \
                    -e AWS_SESSION_TOKEN \
                    ${jenkins.ACCOUNTS['shd']}.dkr.ecr.us-east-1.amazonaws.com/ecr-public/aws-cli/aws-cli:latest \
                    lambda update-function-code \
                        --region ${region} \
                        --function-name ${lambdaFunctionName} \
                        --image-uri "${imageURI}"
            """
                }
                jenkins.echo("Rollback successful for lambda: ${lambdaFunctionName} with previousVersion: ${previousVersion}")
            }
        } catch(Exception exception) {
            jenkins.notifyFailure(projectName, "Rollback failed while attempting to deploy ${previousVersion}", exception)
        }
    }

    @Override
    protected void initializeStages() {
        build = Stage.builder().runner(this.&buildLambda).build()
        validate = new SonarqubeStage(name)
        publish = Stage.builder().runner(this.&publishLambda).build()
        regionDeploy = Stage.builder().runner(this.&deployLambda).build()
        regionRollback = Stage.builder().runner(this.&rollbackLambda).build()
    }
}
