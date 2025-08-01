package pipelines.module

import groovy.transform.builder.Builder
import hudson.model.Result
import pipelines.PipelineConfig

class DatadogMonitorModule extends Module {

    def postCheckAttemptsPrd
    def postCheckIntervalSecondsPrd
    def postCheckAttemptsNonPrd
    def postCheckIntervalSecondsNonPrd
    String prdFailureStatus
    String nonPrdFailureStatus
    Boolean autoRollbackOnFailure
    private Map<String, String> ddKeys = null

    @Builder
    DatadogMonitorModule(def postCheckAttemptsPrd, def postCheckIntervalSecondsPrd,
    def postCheckAttemptsNonPrd, def postCheckIntervalSecondsNonPrd,
    String prdFailureStatus, String nonPrdFailureStatus, Boolean autoRollbackOnFailure) {
        super([name: 'datadog monitor'])

        this.postCheckAttemptsPrd = postCheckAttemptsPrd ?: 16
        this.postCheckIntervalSecondsPrd = postCheckIntervalSecondsPrd ?: 60
        this.postCheckAttemptsNonPrd = postCheckAttemptsNonPrd ?: 5
        this.postCheckIntervalSecondsNonPrd = postCheckIntervalSecondsNonPrd ?: 60
        this.prdFailureStatus = prdFailureStatus ?: 'ERROR'
        this.nonPrdFailureStatus = nonPrdFailureStatus ?: 'ERROR'
        this.autoRollbackOnFailure = autoRollbackOnFailure ?: true
    }

    DatadogMonitorModule(PipelineConfig config, props) {
        super(props)

        this.postCheckAttemptsPrd = props.postCheckAttemptsPrd ?: 16
        this.postCheckIntervalSecondsPrd = props.postCheckIntervalSecondsPrd ?: 60
        this.postCheckAttemptsNonPrd = props.postCheckAttemptsNonPrd ?: 5
        this.postCheckIntervalSecondsNonPrd = props.postCheckIntervalSecondsNonPrd ?: 60
        this.prdFailureStatus = props.prdFailureStatus ?: 'ERROR'
        this.nonPrdFailureStatus = props.nonPrdFailureStatus ?: 'ERROR'
        this.autoRollbackOnFailure = props.autoRollbackOnFailure ?: true
    }

    List<Object> getFailingMonitors(def jenkins, String projectName, String env, Boolean isPostDeployment) {
        def tags = "tag%3Aapp%3A${projectName}"
        tags += "%20tag%3Aenv%3A${env}"
        tags += "%20muted%3Afalse"
        tags += "%20tag%3Adeployment-gate"
        // Exclude pre-check monitors if this is post-deployment check
        if (isPostDeployment) {
            tags += "%20-tag%3Apre-check"
        } else {
            tags += "%20-tag%3Apost-check"
        }

        String url = "https://api.datadoghq.com/api/v1/monitor/search?query=${tags}"
        def response = jenkins.httpRequest url: url, customHeaders: buildHeaders(jenkins, env)
        def json = jenkins.readJSON text: response.content

        return json.monitors.findAll { monitor -> monitor.status != "OK" && monitor.status != "No Data" }
    }

    Map<String, String> getDDKeys(def jenkins, String env) {
        def secretPrefix = "/chewy/${env}/us-east-1/datadog-slo/datadog"

        jenkins.sh "${jenkins.ecrLogin()}"
        jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS[env]}:role/CHEWY-cross-jenkins") {
            return [appKey: jenkins.secretsmanager.getKVSecret("${secretPrefix}-app-key"),
                apiKey: jenkins.secretsmanager.getKVSecret("${secretPrefix}-api-key")]
        }
    }

    Boolean shouldCheckMonitors(ModuleConfig config) {
        !config.skipDeploymentGates
    }

    Boolean checkMonitors(ModuleConfig config, Boolean isPostDeployment) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def env = config.env

        def passing = true
        def currentTry = 0
        def failingMonitors = []

        def maxRetries = 0
        def waitTimeSeconds = 0
        Boolean isProd = 'prd'.equalsIgnoreCase(env)
        if (isPostDeployment) {
            maxRetries = isProd ? postCheckAttemptsPrd : postCheckAttemptsNonPrd
            waitTimeSeconds = isProd ? postCheckIntervalSecondsPrd : postCheckIntervalSecondsNonPrd
        }

        while (currentTry <= maxRetries) {
            failingMonitors = getFailingMonitors(jenkins, projectName, env, isPostDeployment)
            if (failingMonitors) {
                def output = [
                    "(${projectName}) ${failingMonitors.size()}:${env} monitor(s) in bad state"
                ]
                for (monitor in failingMonitors) {
                    GString link = "https://app.datadoghq.com/monitors/${monitor.id}"
                    output += "***** ${monitor.name} ( ${link} ) is failed with status ${monitor.status}"
                }
                jenkins.echo(String.join("\n", output))
                passing = false
                break
            }
            if (waitTimeSeconds > 0) {
                jenkins.sleep(waitTimeSeconds)
            }
            currentTry++
        }
        if (passing) {
            jenkins.echo("Datadog Monitor checks success")
            return true
        } else {
            def message = "(${projectName}) ${failingMonitors.size()}:${env} datadog monitor(s) in bad state"
            String failureStatus = isProd ? prdFailureStatus : nonPrdFailureStatus
            if ('ERROR'.equalsIgnoreCase(failureStatus)) {
                if (config.rollback && autoRollbackOnFailure) {
                    config.rollback.run()
                }
                jenkins.notifyFailure(projectName, message)
                jenkins.error("Failed app monitor checks")
                jenkins.currentBuild.result = Result.FAILURE.toString()
                return false
            } else if ('WARN'.equalsIgnoreCase(failureStatus)) {
                jenkins.echo("Failed app monitor checks")
                jenkins.currentBuild.result = Result.UNSTABLE.toString()
                return true
            }
            return false
        }
    }

    @Override
    protected void initializeStages() {
        preCheck = new Stage({ config -> checkMonitors(config, false) }, this.&shouldCheckMonitors)
        postCheck = new Stage({ config -> checkMonitors(config, true) }, this.&shouldCheckMonitors)
    }

    private void buildHeaders(def jenkins, String env) {
        if (ddKeys == null) {
            ddKeys = getDDKeys(jenkins, env)
        }
        [
            [name: "Accept", value: "application/json"],
            [maskValue: true, name: "DD-API-KEY", value: "${ddKeys.apiKey}"],
            [maskValue: true, name: "DD-APPLICATION-KEY", value: "${ddKeys.appKey}"],
        ]
    }
}
