package pipelines.module

import hudson.model.Result
import pipelines.GatlingModuleConfig
import pipelines.PipelineConfig

class GatlingModule extends Module {

    def gatlingModuleConfig

    GatlingModule(GatlingModuleConfig config) {
        super([name: "gatling ${config.name}"])
        this.gatlingModuleConfig = config
    }

    GatlingModule(PipelineConfig config, props) {
        super(props)
        this.gatlingModuleConfig = new GatlingModuleConfig(config, props)
    }

    Boolean shouldRunTests(ModuleConfig config) {
        // Run tests if deployment gates aren't skipped, we're in stage and we're either
        // running a non-merge-queue branch deploy with runInBranchBuild enabled,
        // running a PR build with runInPrBuild enabled
        // running a merge-queue build with runInMergeQueueBuild enabled
        // doing none of the above, which is a main build, with runInMainBuild enabled
        return !config.skipDeploymentGates && config.env == 'stg' &&
                ((config.isBranchDeploy && !config.isMergeQueueBuild && gatlingModuleConfig.runInBranchBuild) ||
                (config.isPrBuild && gatlingModuleConfig.runInPrBuild) ||
                (config.isMergeQueueBuild && gatlingModuleConfig.runInMergeQueueBuild) ||
                (!config.isBranchDeploy && !config.isMergeQueueBuild && !config.isPrBuild && gatlingModuleConfig.runInMainBuild))
    }

    Boolean runGatlingTests(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def namespace = config.namespace
        def simulationId = gatlingModuleConfig.simulationId
        def slackChannel = gatlingModuleConfig.slackChannel
        def disableSlackNotifications = gatlingModuleConfig.disableSlackNotifications ||
                (config.isPrBuild || config.isBranchDeploy || config.isMergeQueueBuild) // disabled for feature branches
        def isBranchDeploy = config.isBranchDeploy
        def isPrBuild = config.isPrBuild
        String failureStatus = gatlingModuleConfig.failureStatus

        // Get the feature branch namespace, if we're on a feature branch, to pass to Gatling
        if (isBranchDeploy) {
            namespace = jenkins.k8s.generateNamespaceName()
        }

        try {
            // Kick off the Gatling test
            jenkins.perf.testWithNamespace(environment: env, simulationId: simulationId, namespace: namespace,
            slackChannel: slackChannel, withNotifications: !disableSlackNotifications)

            // Parse performance test summary
            boolean failedAssertions = false
            def files = jenkins.findFiles(glob: '**/gatlingFrontLineJunitResults/*.xml')
            files.each { file ->
                def xml = jenkins.readFile(file.getPath())
                def testsuite = new XmlSlurper().parseText(xml)

                String name = testsuite.'@name'.text().substring(testsuite.'@name'.text().lastIndexOf('.') + 1)
                String testCount = testsuite.'@tests'.text()
                String errorCount = testsuite.'@errors'.text()
                String failureCount = testsuite.'@failures'.text()

                if (Integer.valueOf(failureCount) > 0) {
                    failedAssertions = true;
                }

                String successes = 'Success:\n'
                String failures = 'Failure:\n'
                testsuite.testcase.each { test ->
                    def status = test.'@status'.text()
                    if (status == 'true') {
                        successes += "\n* ${test.'system-out'.text()}"
                    } else {
                        failures += "\n* ${test.'@name'.text()}\n  - ${test.failure.text()}"
                    }
                }

                String summary = """\
                    |Gatling Performance - ${name}
                    |Total Assertions: ${testCount} | Errors: ${errorCount} | Failures: ${failureCount}
                    |
                    |${successes}
                    |
                    |${failures}
                  """.stripMargin()

                // Log summary to jenkins console
                jenkins.echo(summary)

                // Leave a PR comment of the performance test summary for PR builds
                if (isPrBuild) {
                    jenkins.cicd.updateComment(summary)
                }
            }

            if (failedAssertions) {
                jenkins.error('Failed Assertions')
            }
            return true
        } catch (Exception e) {
            if ("WARN".compareToIgnoreCase(failureStatus) || "WARNING".compareToIgnoreCase(failureStatus)) {
                jenkins.echo("Gatling performance test failed: ${e.getLocalizedMessage()}")
                jenkins.currentBuild.result = Result.UNSTABLE.toString()
                return true
            } else {
                jenkins.error("Gatling performance test failed: ${e.getLocalizedMessage()}")
                jenkins.currentBuild.result = Result.FAILURE.toString()
                return false
            }
        }
    }

    @Override
    protected void initializeStages() {
        postCheck = new Stage(this.&runGatlingTests, this.&shouldRunTests)
    }
}
