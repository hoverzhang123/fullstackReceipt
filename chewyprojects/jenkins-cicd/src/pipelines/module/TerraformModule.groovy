package pipelines.module

import pipelines.PipelineConfig
import pipelines.module.stage.DockerBuildStage
import pipelines.metadata.CommonUtil

class TerraformModule extends Module {
    final Integer NO_CHANGES_EXIT_CODE = 0

    TerraformModule(String name, String ecrRepo = null) {
        super([name: name, type: 'terraform',ecrRepo: ecrRepo])
    }

    TerraformModule(PipelineConfig config, props) {
        super(props)
    }

    void publishTerraform(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def vertical = config.vertical
        jenkins.env.DEPLOY_STEP = "Publish Terraform - ${projectVersion}"

        jenkins.sh "${jenkins.ecrLogin()}"
        def image = CommonUtil.getImageBaseUrl(jenkins, props.ecrRepo, vertical, projectName)

        if (config.previousVersion) {
            jenkins.sh """#!/bin/bash
                    docker tag ${image}:terraform-${config.previousVersion} ${image}:terraform-${projectVersion}
                """
        }

        jenkins.sh "docker push ${image}:terraform-${projectVersion}"
    }

    void terraformPlan(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def vertical = config.vertical

        jenkins.env.DEPLOY_STEP = "Terraform Plan"
        def image = CommonUtil.getImageBaseUrl(jenkins, props.ecrRepo, vertical, projectName)
        String imageURI = "${image}:${name}-${projectVersion}"

        if (config.isBranchDeploy) {
            def changeUrl = jenkins.env.CHANGE_URL

            // If this is not a pr-merge build, try to find the PR
            if (!changeUrl) {
                String head = "Chewy-Inc:${jenkins.env.GIT_BRANCH}"
                String url = "https://api.github.com/repos/Chewy-Inc/${projectName}/pulls?head=${head}&sort=created&direction=desc"
                def headers = [
                    [name: "Accept", value: "application/vnd.github+json"],
                    [maskValue: true, name: "Authorization", value: "Bearer ${jenkins.env.GITHUB_TOKEN}"],
                    [name: "X-GitHub-Api-Version", value: "2022-11-28"],
                ]
                def response = jenkins.httpRequest url: url, customHeaders: headers
                def pullRequests = jenkins.readJSON text: response.content
                def thisPR = pullRequests.find { pr -> pr.head.sha == jenkins.env.GIT_COMMIT }
                if (thisPR) {
                    changeUrl = thisPR.url
                } else {
                    jenkins.echo("Could not find PR!!!!")
                }
            }

            jenkins.env.GITHUB_REPO_NAME = projectName
            def prUrlParam = changeUrl ? "-e PULL_REQUEST_URL=${changeUrl} \\" : "\\"
            jenkins.sh """#!/bin/bash
            docker run \\
                --entrypoint /terraform/entrypoint-tfnotify.sh \\
                -e AWS_ACCESS_KEY_ID \\
                -e AWS_SECRET_ACCESS_KEY \\
                -e AWS_SESSION_TOKEN \\
                ${prUrlParam}
                -e GITHUB_TOKEN \\
                -e GITHUB_REPO_NAME \\
                ${imageURI} \\
                ${projectName}
            """
        } else {
            plan(jenkins, imageURI, projectName, 'dev')
        }
    }

    void applyTerraform(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def vertical = config.vertical
        def isAutomatedDeploy = config.isAutomatedDeploy
        def isBranchDeploy = config.isBranchDeploy

        jenkins.env.DEPLOY_STEP = "Deploy Terraform - ${env} ${projectVersion}"

        String dockerTag = projectVersion
        if (name != '') {
            dockerTag = "${name}-${projectVersion}"
        }
        def image = CommonUtil.getImageBaseUrl(jenkins, props.ecrRepo, vertical, projectName)
        String imageURI = "${image}:${dockerTag}"
        jenkins.sh "${jenkins.ecrLogin()}"

        // manual production deployments and PR feature/terraform branch builds
        if ((env == 'prd' && !isAutomatedDeploy) || isBranchDeploy) {
            if (plan(jenkins, imageURI, projectName, env) != NO_CHANGES_EXIT_CODE) {
                jenkins.timeout(time: 10, unit: 'MINUTES') {
                    def consoleUrl = "${jenkins.env.BUILD_URL}/console"
                    def inputUrl = "${jenkins.env.BUILD_URL}/input"
                    jenkins.notify(projectName, "Approve Terraform plan? (<${inputUrl}|Input Options>) - (<${consoleUrl}|Console>)", "blue", true)
                    jenkins.input id: 'terraform-apply', message: "Approve Terraform plan for ${projectName} ${env}"
                }
            }
        }
        jenkins.sh """
            docker run \
                -e AWS_ACCESS_KEY_ID \
                -e AWS_SECRET_ACCESS_KEY \
                -e AWS_SESSION_TOKEN \
                ${imageURI} \
                ${projectName} \\
                ${env} apply -auto-approve
            """
    }

    /**
     0 = Succeeded with empty diff (no changes)
     1 = Error
     2 = Succeeded with non-empty diff (changes present)
     **/
    private static Integer plan(Object jenkins, String image, String projectName, String env) {
        def statusCode = jenkins.sh(
                returnStatus: true,
                script: """
                #!/bin/bash
                docker run \
                    -e AWS_ACCESS_KEY_ID \
                    -e AWS_SECRET_ACCESS_KEY \
                    -e AWS_SESSION_TOKEN \
                    ${image} \
                    ${projectName} \
                    ${env} plan -lock-timeout=10m -detailed-exitcode
                """
                )
        return statusCode
    }

    @Override
    protected void initializeStages() {
        build = new DockerBuildStage(name)
        validate = Stage.builder().runner(this.&terraformPlan).build()
        publish = Stage.builder().runner(this.&publishTerraform).build()
        //TODO: preDeploy: [this.&verifyTerraform] as Stage?
        globalDeploy = Stage.builder().runner(this.&applyTerraform).build()
    }
}
