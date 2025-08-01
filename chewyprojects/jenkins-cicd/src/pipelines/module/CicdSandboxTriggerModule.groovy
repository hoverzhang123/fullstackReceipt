package pipelines.module

import hudson.model.Result
import pipelines.PipelineConfig

class CicdSandboxTriggerModule extends Module {
    // Constants for better maintainability
    private static final String DEFAULT_TEST_ENVIRONMENT = 'ALL'
    private static final String DEFAULT_DEPLOYMENT_TYPE = 'BRANCH'
    private static final String BUILD_SUCCESS = 'SUCCESS'
    private static final String DEFAULT_JOB_PATH = "build-cicd-sandbox/main"

    String testEnvironment
    String deploymentType
    String sandboxJobPath

    CicdSandboxTriggerModule(PipelineConfig config, Object props) {
        super(props)
        this.testEnvironment = props.testEnvironment ?: DEFAULT_TEST_ENVIRONMENT
        this.deploymentType = props.deploymentType ?: DEFAULT_DEPLOYMENT_TYPE
        this.sandboxJobPath = props.sandboxJobPath ?: DEFAULT_JOB_PATH
    }

    Boolean shouldTriggerSandbox(ModuleConfig config) {
        def jenkins = config.jenkins

        if (config.isPrBuild) {
            jenkins.echo("Triggering cicd-sandbox for PR build: ${jenkins.env.CHANGE_ID}")
            return true
        }
        jenkins.echo("Skipping cicd-sandbox trigger - not enabled for this build type")
        return false
    }

    Boolean triggerSandboxTest(ModuleConfig config) {
        def jenkins = config.jenkins

        // Determine the jenkins-cicd branch to use
        String jenkinsCicdBranch = jenkins.env.CHANGE_BRANCH

        // Validate required parameters
        if (!jenkinsCicdBranch) {
            jenkins.echo("ERROR: CHANGE_BRANCH is not set, cannot determine jenkins-cicd branch")
            return false
        }

        try {
            jenkins.echo("Triggering sandbox job: ${sandboxJobPath} with branch: ${jenkinsCicdBranch}")

            // Build the parameters for the cicd-sandbox job
            def buildParameters = [
                jenkins.string(name: 'JENKINS_CICD_BRANCH', value: jenkinsCicdBranch),
                jenkins.string(name: 'TEST_ENVIRONMENT', value: testEnvironment),
                jenkins.string(name: 'DEPLOYMENT_TYPE', value: deploymentType),
            ]

            // Trigger the cicd-sandbox job and wait for completion
            def sandboxBuild = jenkins.build(
                    job: sandboxJobPath,
                    parameters: buildParameters,
                    wait: true,
                    propagate: false  // Don't fail immediately, let us handle the result
                    )

            jenkins.echo("Sandbox job completed with result: ${sandboxBuild.result}")
            jenkins.echo("Sandbox job URL: ${sandboxBuild.absoluteUrl}")

            // Check the result
            if (sandboxBuild.result == BUILD_SUCCESS) {
                jenkins.echo("CICD Sandbox test passed successfully")
                return true
            } else {
                jenkins.echo("CICD Sandbox test failed with result: ${sandboxBuild.result}")
                return false
            }

        } catch (Exception e) {
            jenkins.echo("Failed to trigger cicd-sandbox job: ${e.message}")
            return false
        }
    }

    Boolean checkSandboxTest(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def env = config.env

        jenkins.env.DEPLOY_STEP = "CICD Sandbox Test - ${projectName}"

        Boolean testPassed = triggerSandboxTest(config)

        if (testPassed) {
            jenkins.echo("CICD Sandbox test validation successful")
            return true
        } else {
            def message = "(${projectName}) CICD Sandbox test failed in ${env} environment"
            jenkins.echo(message)

            // Make the build fail so that the validate phase of jenkins-cicd fails
            jenkins.currentBuild.result = Result.FAILURE.toString()
            jenkins.error("CICD Sandbox test failed - failing the build")
            return false
        }
    }

    @Override
    protected void initializeStages() {
        // Only initialize post-check stage since we want to run this after deployment
        validate = new Stage(this.&checkSandboxTest, this.&shouldTriggerSandbox)
    }
}
