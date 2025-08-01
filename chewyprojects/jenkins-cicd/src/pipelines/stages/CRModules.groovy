package pipelines.stages

import groovy.json.JsonBuilder
import hudson.model.ParametersAction
import pipelines.PipelineConfig
import pipelines.metadata.CommonUtil
import pipelines.metadata.S3Util
import pipelines.module.ModuleProps

class CRModules {

    static String SKIP_AUTOMATED_DEPLOYMENT_TAG = 'skip-automated-deployments'

    def jenkins
    String projectName
    List<String> regions
    String assignmentGroup
    String namespace
    String clusterName
    List<List<String>> deploymentLowerEnvironments

    // Internal
    String tag
    String crIncNumber
    Boolean emergency
    String notificationColor = 'blue'
    S3Util s3Util
    PipelineConfig pipelineConfig

    // Options
    Boolean useSnowTestEnvironment = false
    Boolean isRollback = false
    Boolean isAutomatedDeploy = false

    CRModules(
    PipelineConfig pipelineConfig,
    Boolean isRollback = false,
    Boolean isAutomatedDeploy = false
    ) {
        this.jenkins = pipelineConfig.jenkins
        this.s3Util = new S3Util(pipelineConfig.jenkins)
        this.projectName = pipelineConfig.projectName
        this.regions = pipelineConfig.regions
        this.assignmentGroup = pipelineConfig.assignmentGroup
        this.clusterName = pipelineConfig.clusterName
        this.namespace = pipelineConfig.namespace
        this.deploymentLowerEnvironments = pipelineConfig.deploymentLowerEnvironments
        this.isRollback = isRollback
        this.isAutomatedDeploy = isAutomatedDeploy
        this.crIncNumber = jenkins.params.CR_INC_NUMBER
        this.emergency = jenkins.params.EMERGENCY
        this.pipelineConfig = pipelineConfig
    }

    String getTag() {
        getGithubToken()
        if (isRollback) {
            // rollback is only supported by apt-shared. Fix this once rxp is onboarded
            return tag = CommonUtil.getRollbackVersion(jenkins, projectName, namespace == "rxp" ? PipelineConfig.PET_HEALTH_NAMESPACE : namespace, 'prd')
        }
        return tag = isAutomatedDeploy ? getLatestTag() : jenkins.params.TAG
    }

    String run() {
        getGithubToken()

        if (shouldSkipDeploy()) {
            jenkins.error('CRModule should not be run if a deployment is not required.')
        }
        if (crIncNumber?.trim()) {
            jenkins.stage('Start manual CR') {
                jenkins.env.CR_NUMBER = crIncNumber
                startManualProdCR()
            }
        } else {
            if (isAutomatedDeploy) {
                jenkins.stage('Start daily automated CR') {
                    startAutomatedProdCR()
                }
            } else {
                jenkins.stage('Start automated CR') {
                    startAutomatedProdCR()
                }
            }
        }
        return tag.replaceAll('v', '')
    }

    /* close CR automatically for automated daily production deployments */
    /* currently does not support failure closing */
    void closeCR(Boolean successful = true) {
        if (!isAutomatedDeploy) {
            jenkins.notify(projectName, "CR MUST BE CLOSED MANUALLY (<https://chewy.service-now.com/task.do?sysparm_query=number=${jenkins.env.CR_NUMBER}|${jenkins.env.CR_NUMBER}>)", notificationColor, true)
            return
        }

        if (successful) {
            jenkins.echo('closing CR as successful')
            def close_data = [
                'substate': 'CLOSED_SUCCESSFUL',
                'was_the_change_properly_validated': true,
                'did_we_achieve_the_expected_outcome': true,
            ]
            jenkins.notify(projectName, 'CR closed as successful', notificationColor, true)
            jenkins.boreas.closeCR(jenkins.env.CR_NUMBER, close_data)
        } else {
            jenkins.notifyFailure(projectName, 'CR paused: failed monitors')
            /*
             def close_data = [
             'substate': 'CLOSED_CANCELLED',
             'was_the_change_properly_validated': true,
             'did_we_achieve_the_expected_outcome': false,
             'substate_details': "${jenkins.env.CR_NUMBER} cancelled due to failed monitors"
             ]
             */
            // do not close CRs automatically. Uncomment below when ready to close failed CRs
            //jenkins.notifyFailure(projectName, "CR cancelled: failed monitors")
            //jenkins.boreas.closeCR(jenkins.env.CR_NUMBER, close_data)
        }
    }

    boolean shouldSkipDeploy() {
        if (isRollback) {
            return false
        }
        if (tag == null) {
            tag = isAutomatedDeploy ? getLatestTag() : jenkins.params.TAG
        }
        if (isAutomatedDeploy) {
            if (gitTagExists(SKIP_AUTOMATED_DEPLOYMENT_TAG)) {
                jenkins.echo("Skipping automated deployment due to ${SKIP_AUTOMATED_DEPLOYMENT_TAG} tag")
                return true
            }
            // verify successful STG deployment metadata
            for (String previousEnv: S3Util.getPreviousDeployedEnvs(deploymentLowerEnvironments, 'prd')) {
                if (!s3Util.validateSuccessfulDeploymentMetadata(pipelineConfig, tag, previousEnv)) {
                    jenkins.echo('Previous environment deployment failed')
                    return true
                }
            }
            // verify existence of PRD deployment metadata
            if (s3Util.validateExistenceOfDeploymentMetadata(pipelineConfig, tag, 'prd')) {
                jenkins.echo("Deployment previously succeeded for tag ${tag}... skipping deployment")
                return true;
            }
        }
        return false
    }

    private static String parseReleaseNotes(String releaseNotes) {
        // Replace single quote with a backtick (service now API returns replaces single quote with &#39;)
        releaseNotes = releaseNotes.replaceAll("'", "`");
        // Replace escaped sequences with newlines
        releaseNotes = releaseNotes.replaceAll("\\\\r\\\\n", "\n");

        return releaseNotes;
    }

    private String getPreviousTag() {
        jenkins.env.DEPLOY_STEP = 'Get previous version'
        def region = regions.first()
        def previousBuild = jenkins.currentBuild.getPreviousSuccessfulBuild()
        def previousVersion = previousBuild.getRawBuild()
                .actions.find { it instanceof ParametersAction }?.parameters?.find { it.name == 'TAG' }?.value
        jenkins.echo("getPreviousTag = ${previousVersion}")
        // get currently deployed helm chart version
        if (previousVersion == PipelineConfig.LATEST_TAG) {
            jenkins.sh "${jenkins.ecrLogin()}"
            jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS['prd']}:role/CHEWY-cross-jenkins") {
                jenkins.docker.image(ModuleProps.CHEWY_TERRAFORM_IMAGE)
                        .inside("-e HOME=${jenkins.env.WORKSPACE} --entrypoint=''") {
                            def cluster = "prd-${jenkins.REGIONS[region]}-${clusterName}"
                            jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${cluster} --alias ${cluster} --kubeconfig ./kube-config-${region}-${cluster}"

                            def getDeployedVersion = "helm history ${projectName} -n ${namespace} --kubeconfig ./kube-config-${region}-${cluster}" +
                                    '| grep deployed ' +
                                    '| tail -1 ' +
                                    "| awk -F'\\t' '{print \$5}'"
                            previousVersion = jenkins.sh(returnStdout: true, script:getDeployedVersion).split('-')[0].trim()
                        }
            }
        }
        jenkins.echo("previous successful build version = ${previousVersion}")
        return previousVersion ?: 'unknown rollback version'
    }

    private String getAssignedUserForCR() {
        String email
        if (isAutomatedDeploy) {
            email = jenkins.getUserEmailFromTagCommit(tag)
        } else {
            email = jenkins.getUserEmailFromManualJenkinsJob()
        }
        jenkins.env.USER_EMAIL = email
        return email
    }

    private String generateReleaseNotes() {
        jenkins.env.DEPLOY_STEP = 'Generate release notes'

        def releaseNotesUrl = "https://api.github.com/repos/Chewy-Inc/${projectName}/releases/generate-notes"
        def releaseNotesHeaders = [
            [name: 'Accept', value: 'application/vnd.github+json'],
            [maskValue: true, name: 'Authorization', value: "Bearer ${jenkins.env.GITHUB_TOKEN}"],
            [name: 'X-GitHub-Api-Version', value: '2022-11-28'],
        ]
        def previousTag = getPreviousTag()
        def releaseNotesData = new JsonBuilder([
            "tag_name": "${tag}",
            "previous_tag_name": "${previousTag}",
        ]).toString()

        jenkins.echo(releaseNotesData)
        def releaseNotesResponse = jenkins.httpRequest url: releaseNotesUrl, httpMode: 'POST', customHeaders: releaseNotesHeaders,
        contentType: 'APPLICATION_JSON', requestBody: releaseNotesData, validResponseCodes: '200'
        if (releaseNotesResponse.status != 200) {
            jenkins.error('failed to generate release notes')
        }

        def releaseNotes = jenkins.readJSON text: releaseNotesResponse.content
        return parseReleaseNotes(releaseNotes.body)
    }

    private Boolean gitTagExists(String gitVersionTag = tag) {
        getGithubToken()
        def tagUrl = "https://api.github.com/repos/Chewy-Inc/${projectName}/git/ref/tags/${gitVersionTag}"
        def tagHeaders = [
            [name: 'Accept', value: 'application/vnd.github+json'],
            [maskValue: true, name: 'Authorization', value: "Bearer ${jenkins.env.GITHUB_TOKEN}"],
            [name: 'X-GitHub-Api-Version', value: '2022-11-28'],
        ]
        def tagResponse = jenkins.httpRequest url: tagUrl, httpMode: 'GET', customHeaders: tagHeaders,
        contentType: 'APPLICATION_JSON', validResponseCodes: '200,404'

        if (tagResponse.status == 200) {
            jenkins.echo("Git tag ${gitVersionTag} exists")
            return true
        } else if (tagResponse.status == 404) {
            jenkins.echo("Git tag ${gitVersionTag} does not exist")
            return false
        } else {
            jenkins.error("gitTagExists returned an invalid status code: ${tagResponse.status}")
            return false
        }
    }


    private String gitReleaseExists(String gitVersionTag = tag) {
        getGithubToken()
        def releaseUrl = "https://api.github.com/repos/Chewy-Inc/${projectName}/releases/tags/${gitVersionTag}"
        def releaseHeaders = [
            [name: 'Accept', value: 'application/vnd.github+json'],
            [maskValue: true, name: 'Authorization', value: "Bearer ${jenkins.env.GITHUB_TOKEN}"],
            [name: 'X-GitHub-Api-Version', value: '2022-11-28'],
        ]
        def releaseResponse = jenkins.httpRequest url: releaseUrl, httpMode: 'GET', customHeaders: releaseHeaders,
        contentType: 'APPLICATION_JSON', validResponseCodes: '200,404'
        if (releaseResponse.status == 200) {
            jenkins.echo("Git release for tag ${gitVersionTag} already exists... skip release generation")
            def releaseNotes = jenkins.readJSON text: releaseResponse.content
            return parseReleaseNotes(releaseNotes.body)
        } else if (releaseResponse.status == 404) {
            jenkins.echo("Git release for tag ${gitVersionTag} does not exist... proceed with release generation")
            return "false"
        } else {
            jenkins.error("gitReleaseExists returned an invalid status code: ${releaseResponse.status}")
            return "false"
        }
    }

    private String findOrCreateGitRelease() {
        jenkins.env.DEPLOY_STEP = 'Generate Git release'
        String gitRelease = gitReleaseExists()
        if (gitRelease != "false") {
            return gitRelease
        }
        def releaseUrl = "https://api.github.com/repos/Chewy-Inc/${projectName}/releases"
        def releaseHeaders = [
            [name: 'Accept', value: 'application/vnd.github+json'],
            [maskValue: true, name: 'Authorization', value: "Bearer ${jenkins.env.GITHUB_TOKEN}"],
            [name: 'X-GitHub-Api-Version', value: '2022-11-28'],
        ]
        def gitReleaseNotes = generateReleaseNotes()
        def releaseData = new JsonBuilder([
            "tag_name": "${tag}",
            "name": "${tag}",
            "make_latest": "true",
            "body": "${gitReleaseNotes}",
        ]).toString()

        def releaseResponse = jenkins.httpRequest url: releaseUrl, httpMode: 'POST', customHeaders: releaseHeaders,
        contentType: 'APPLICATION_JSON', requestBody: releaseData, validResponseCodes: '201'
        if (releaseResponse.status != 201) {
            jenkins.error('failed to generate git release')
        }
        return gitReleaseNotes
    }

    /* validates and starts the CR */
    private void startManualProdCR() {
        jenkins.env.DEPLOY_STEP = 'Start CR (Manual)'
        jenkins.notify(projectName, "Manual prod deploy (<https://chewy.service-now.com/task.do?sysparm_query=number=${jenkins.env.CR_NUMBER}|${jenkins.env.CR_NUMBER}>)", notificationColor, false, true)
        jenkins.boreas.validateAndStartChange(crIncNumber, emergency, getSnowEnvironment(), pipelineConfig.respectChangeFreezesProd)
    }

    /* retrieves contributors from git commits and converts them to slack user ids */
    private String getContributors() {
        /* example commit user would be 123456789+dwoo-chwy@users.noreply.github.com */
        jenkins.echo(getPreviousTag())
        jenkins.echo(tag)
        String usernames = jenkins.sh(returnStdout: true, script: "git show -s --format='%ae' ${getPreviousTag()}...${tag} --no-patch | awk -F'[+-]' '{print \$2}' | sort | uniq").trim()
        if (!usernames || usernames.isEmpty()) {
            return "no or unknown contributors"
        }
        jenkins.echo(usernames)
        // returns a list of slack ids e.g. @dwoo @dwoo2 @dwoo3
        List<Integer> userIds = usernames.split("\n").collect(({ user ->
            return "@${user}"
        } as Closure<Integer>))
        return userIds.join(" ")
    }

    /* generates the change request and starts the CR */
    private void startAutomatedProdCR() {
        jenkins.env.DEPLOY_STEP = 'Start CR (Standard)'
        String snow_environment = getSnowEnvironment()
        def user = getAssignedUserForCR()
        // prepare SNOW standard change template
        def open_data = [
            'assignment_group': assignmentGroup,
            'assigned_to'     : user,
            'template_name'   : "${projectName} - Incremental Release",
        ]
        def gitReleaseNotes = findOrCreateGitRelease()
        /* generate ServiceNow standard change request */
        jenkins.env.CR_NUMBER = jenkins.boreas.createCR(open_data, snow_environment)
        jenkins.echo("CR_NUMBER=${jenkins.env.CR_NUMBER}")
        def previousTag = getPreviousTag()

        def render_data = [
            'template_model': [
                [
                    'name' : 'app_name',
                    'value': projectName
                ],
                [
                    'name' : 'app_version',
                    'value': tag
                ],
                [
                    'name' : 'backout_version',
                    'value': previousTag
                ],
                [
                    'name' : 'git_url',
                    'value': gitReleaseNotes
                ],
                [
                    'name' : 'git_release_notes',
                    'value': gitReleaseNotes
                ],
                [
                    'name' : 'eks_cluster',
                    'value': clusterName
                ],
                [
                    'name' : 'regions',
                    'value': regions.join(', ')
                ],
            ],
        ]

        /* render ServiceNow standard change request template */
        jenkins.boreas.renderCR(jenkins.env.CR_NUMBER, render_data, snow_environment)
        if (isAutomatedDeploy) {
            jenkins.notify(projectName, "Daily automated prod deploy (<https://chewy.service-now.com/task.do?sysparm_query=number=${jenkins.env.CR_NUMBER}|${jenkins.env.CR_NUMBER}>)", notificationColor, true, true)
            jenkins.notify(projectName, "All related contributors: ${getContributors()}", notificationColor)
        } else {
            jenkins.notify(projectName, "Standard prod ${isRollback ? PipelineConfig.ROLLBACK : PipelineConfig.DEPLOY} (<https://chewy.service-now.com/task.do?sysparm_query=number=${jenkins.env.CR_NUMBER}|${jenkins.env.CR_NUMBER}>)", notificationColor, false, true)
        }
        jenkins.boreas.startCR(jenkins.env.CR_NUMBER, snow_environment, user, pipelineConfig.respectChangeFreezesProd)
    }

    private String getSnowEnvironment() {
        def snow_environment = useSnowTestEnvironment
                ? 'https://servicenow-integration-service-preprod.shss.chewy.com'
                : 'https://servicenow-integration-service.shss.chewy.com'
        snow_environment
    }

    private String getLatestTag() {
        // grab tags, grab all semver ones, sort by verision, then return the greatest tag
        String tag = jenkins.sh(returnStdout: true, script:"git tag | grep -E '^[0-9]+\\.[0-9]+\\.[0-9]+\$' | sort -Vr | head -n 1")

        // throw error if the returned tag is null or invalid
        if (!tag || tag.isEmpty() || tag.isBlank()) {
            jenkins.notifyFailure(projectName, 'no valid tag to deploy')
            jenkins.error("No valid tag")
        }
        return tag.trim()
    }

    private void getGithubToken() {
        jenkins.withCredentials([
            jenkins.usernamePassword(credentialsId: 'jenkins-github-userpass', passwordVariable: 'GRGIT_PASS', usernameVariable: 'GRGIT_USER')
        ]) {
            jenkins.env.GITHUB_TOKEN = jenkins.env.GRGIT_PASS
        }
    }
}
