package pipelines

import hudson.model.BooleanParameterDefinition
import hudson.model.ChoiceParameterDefinition
import hudson.model.ParameterValue
import hudson.model.Result
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import hudson.model.StringParameterValue
import net.uaznia.lukanus.hudson.plugins.gitparameter.GitParameterDefinition
import net.uaznia.lukanus.hudson.plugins.gitparameter.SelectedValue
import net.uaznia.lukanus.hudson.plugins.gitparameter.SortMode

import static pipelines.github.Auth.withGithubAuth
import static pipelines.github.Repository.isDefaultBranch

class Pipeline {
    def jenkins
    PipelineConfig pipelineConfig

    String[] actions = [
        PipelineConfig.DEPLOY,
        PipelineConfig.ROLLBACK,
    ]
    ParameterValue deploy = new StringParameterValue('ACTION', PipelineConfig.DEPLOY)

    Pipeline(PipelineConfig pipelineConfig) {
        this.pipelineConfig = pipelineConfig
        this.jenkins = pipelineConfig.jenkins
    }

    def defaultParameters() {
        String[] environments = [
            PipelineConfig.CHOOSE_ALL_SELECT_OPTION
        ] + pipelineConfig.deploymentLowerEnvironments.flatten()
        String[] regions = [
            PipelineConfig.CHOOSE_ALL_SELECT_OPTION
        ] + pipelineConfig.regions.flatten()
        return new ParametersDefinitionProperty(
                new ChoiceParameterDefinition('ACTION', actions, 'Which action would you like to take?').copyWithDefaultValue(deploy),
                new ChoiceParameterDefinition('ENVIRONMENT', environments, 'Which environment(s) should be deployed to?'),
                new ChoiceParameterDefinition('REGION', regions, 'Which region(s) should be deployed to?'),
                new BooleanParameterDefinition('SKIP_DEPLOY_GATES', false, 'If checked, the deploy will skip pre/post deployment checks'),
                new GitParameterDefinition('TAG', 'PT_TAG', PipelineConfig.LATEST_TAG, 'Select Version Tag (Default To Latest)', '',
                'origin/(.*)', '*[0-9].*[0-9].[0-9]', SortMode.DESCENDING_SMART, SelectedValue.DEFAULT, pipelineConfig.projectName, true),
                new BooleanParameterDefinition('RESPECT_CHANGE_FREEZE', true,
                'Auto-aborts build if affected by an active change freeze.  If unchecked, the deploy will continue whether or not a change freeze is in effect.'),
                )
    }

    def prodParameters() {
        pipelineConfig.environment = "prd"
        return new ParametersDefinitionProperty(
                new ChoiceParameterDefinition('ACTION', actions, 'Which action would you like to take?').copyWithDefaultValue(deploy),
                new BooleanParameterDefinition('EMERGENCY', false, 'Select for an Emergency Release and include Incident Number (INC###).'),
                new StringParameterDefinition('CR_INC_NUMBER', null, 'Supply existing CR Number (CHG###) or Incident Number (INC###) if applicable.'),
                new BooleanParameterDefinition('SKIP_DEPLOY_GATES', false, 'If checked, the deploy will skip pre/post deployment checks'),
                new GitParameterDefinition('TAG', 'PT_TAG', PipelineConfig.LATEST_TAG, 'Select Version Tag (Default To Latest)', '',
                'origin/(.*)', '*[0-9].*[0-9].[0-9]', SortMode.DESCENDING_SMART, SelectedValue.TOP, pipelineConfig.projectName, true),
                new BooleanParameterDefinition('RESPECT_CHANGE_FREEZE', true,
                'Auto-aborts build if affected by an active change freeze.  If unchecked, the deploy will continue whether or not a change freeze is in effect.'),
                )
    }

    def defaultStages(initStage = null) {
        if (skipCI()) {
            jenkins.echo("Skipping CI due to commit message.")
            if (isMergeQueue()) {
                jenkins.notifyPRMergeStatusForMergeQueue(buildSucceeded: true, repositoryName: pipelineConfig.projectName)
            }
            jenkins.currentBuild.result = Result.SUCCESS.toString()
            return
        }
        if (initStage != null) {
            jenkins.stage("Init Setup Stage") {
                initStage()
            }
        }
        boolean isValid = false
        String environment = pipelineConfig.environment
        if (environment == 'prd') {
            isValid = true
            jenkins.stage("Deploy: Production") {
                manualProdDeploy()
            }
        }
        else if (isBranchPipeline()) {
            isValid = true
            jenkins.stage("Branch") {
                if (pipelineConfig.doBranchBuild()) {
                    branch()
                }
            }
        }
        else if (isMergeQueue()) {
            isValid = true
            jenkins.stage("Merge Queue") {
                branch(true)
            }
        }
        else if (matchesBranchPattern('feature/terraform/.*')
                || matchesBranchPattern('bugfix/terraform/.*')) {
            isValid = true
            jenkins.stage("Branch Apply Terraform") {
                if (pipelineConfig.doBranchBuild()) {
                    branchApplyTerraform()
                }
            }
        } else if (isDefaultBranch(jenkins)
                || matchesBranchPattern('feature/deployment/.*')
                || matchesBranchPattern('hotfix/.*')) {
            isValid = true
            jenkins.stage("Preprod") {
                preprod(matchesBranchPattern('hotfix/.*'))
            }
        }

        // Should never get here -> report build as failed
        if (!isValid) {
            jenkins.notifyFailure(pipelineConfig.projectName, "No default stage matched. Exiting pipeline.")
            jenkins.echo("No default stage matched. Exiting pipeline.")
            jenkins.currentBuild.result = Result.FAILURE.toString()
        }
    }

    def matchesBranchPattern(branchPattern) {
        String branchName = pipelineConfig.branchName
        return branchName && branchName =~ branchPattern
    }

    def isMergeQueue() {
        String branchName = pipelineConfig.branchName
        return branchName && (branchName.contains("gh-readonly-queue") || branchName.contains("feature/merge-queue"))
    }

    Boolean skipCI() {
        String commitMessage = jenkins.getCommitMessageFromCommitHash(jenkins.env.GIT_COMMIT)
        jenkins.echo("Commit message: ${commitMessage}")
        return PipelineConfig.SKIP_CI_STRINGS.any { commitMessage.contains(it) }
    }

    void branch(boolean isMergeQueue = false) {
        // get user email in case of failure
        jenkins.getUserEmailFromGitBuildData()
        pipelineConfig.isMergeQueueBuild = isMergeQueue

        String projectVersion = getVersion(true)
        setBuildDisplayName(projectVersion)
        new BranchPipeline(pipelineConfig, projectVersion).run(isMergeQueue)
    }

    void preprod(boolean skipDeploymentParameters = false) {
        // get user email in case of failure
        jenkins.getUserEmailFromGitBuildData()
        String projectVersion
        if (jenkins.params && jenkins.params.TAG != PipelineConfig.LATEST_TAG) {
            projectVersion = jenkins.params.TAG.replaceAll("v", "")
        } else {
            projectVersion = getVersion(false)
        }
        setBuildDisplayName(projectVersion)
        new PreprodPipeline(pipelineConfig, projectVersion).run(skipDeploymentParameters)
    }

    void manualProdDeploy() {
        // get user email in case of failure
        jenkins.getUserEmailFromManualJenkinsJob()
        new ProdPipeline(pipelineConfig).run()
    }

    void setBuildDisplayName(String projectVersion) {
        // Set the displayName of the current build (example: 123 - My Project:1.23.4)
        String buildNumber = jenkins.currentBuild.number
        String projectName = pipelineConfig.projectName
        jenkins.currentBuild.displayName = "${buildNumber} - ${projectName}:${projectVersion}"
    }

    void branchApplyTerraform() {
        // get user email in case of failure
        jenkins.getUserEmailFromGitBuildData()
        new TerraformBranchPipeline(pipelineConfig, getVersion(true)).run()
    }

    def reportBuildFailure() {
        jenkins.notifyFailure(pipelineConfig.projectName, "${jenkins.env.DEPLOY_STEP}", true)
    }

    def isBranchPipeline() {
        return (!isDefaultBranch(jenkins)
                && !matchesBranchPattern('.*/skip/.*')
                && !matchesBranchPattern('.*/terraform/.*')
                && !matchesBranchPattern('.*/deployment/.*')
                && !matchesBranchPattern('hotfix/.*')
                && !matchesBranchPattern('gh-readonly-queue/.*')
                && !matchesBranchPattern('feature/merge-queue/.*'))
    }

    /**
     *
     * @param prerelease
     *
     * the commit message can also trigger bumps via keywords: [major], [minor], [patch]
     * @return
     */
    String getVersion(Boolean prerelease = true) {
        jenkins.env.DEPLOY_STEP = "Versioning" + prerelease ? " Snapshot" : ""

        withGithubAuth(jenkins) {
            String version
            if (jenkins.params.TAG != PipelineConfig.LATEST_TAG) {
                jenkins.echo("jenkins.params.TAG is not Latest, using ${jenkins.params.TAG} as version")
                version = jenkins.params.TAG.replaceAll('v', '')
            } else {
                version = prerelease ?
                        jenkins.semver.getPreReleaseVersion(pipelineConfig.versionIncrement) :
                        jenkins.semver.getVersion(pipelineConfig.versionIncrement)
                jenkins.echo("getVersion: version ${version} isPrelease ${prerelease}, versionIncrement ${pipelineConfig.versionIncrement}")
                if (!prerelease) {
                    jenkins.github.createTag(version)
                }
            }

            jenkins.sh "echo ${version} > version.txt"

            return version
        }
    }
}
