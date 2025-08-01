package pipelines.module

import groovy.transform.builder.Builder
import hudson.model.Result
import pipelines.PipelineConfig

class DynatraceMonitorModule extends Module {
    def postCheckAttemptsPrd
    def postCheckIntervalSecondsPrd
    def postCheckAttemptsNonPrd
    def postCheckIntervalSecondsNonPrd
    String prdFailureStatus
    String nonPrdFailureStatus
    Boolean autoRollbackOnFailure
    Boolean enableDeployObservability
    private String dynatraceEndpoint
    private String accessToken
    private Map secrets = null

    @Builder
    DynatraceMonitorModule(def postCheckAttemptsPrd, def postCheckIntervalSecondsPrd,
    def postCheckAttemptsNonPrd, def postCheckIntervalSecondsNonPrd,
    String prdFailureStatus, String nonPrdFailureStatus, Boolean autoRollbackOnFailure,
    Boolean enableDeployObservability) {
        super([name: 'dynatrace monitor'])

        this.postCheckAttemptsPrd = postCheckAttemptsPrd ?: 16
        this.postCheckIntervalSecondsPrd = postCheckIntervalSecondsPrd ?: 60
        this.postCheckAttemptsNonPrd = postCheckAttemptsNonPrd ?: 5
        this.postCheckIntervalSecondsNonPrd = postCheckIntervalSecondsNonPrd ?: 60
        this.prdFailureStatus = prdFailureStatus ?: 'ERROR'
        this.nonPrdFailureStatus = nonPrdFailureStatus ?: 'ERROR'
        this.autoRollbackOnFailure = autoRollbackOnFailure ?: true
        this.enableDeployObservability = enableDeployObservability ?: false
    }

    DynatraceMonitorModule(PipelineConfig config, props) {
        super(props)

        this.postCheckAttemptsPrd = props.postCheckAttemptsPrd ?: 16
        this.postCheckIntervalSecondsPrd = props.postCheckIntervalSecondsPrd ?: 60
        this.postCheckAttemptsNonPrd = props.postCheckAttemptsNonPrd ?: 5
        this.postCheckIntervalSecondsNonPrd = props.postCheckIntervalSecondsNonPrd ?: 60
        this.prdFailureStatus = props.prdFailureStatus ?: 'ERROR'
        this.nonPrdFailureStatus = props.nonPrdFailureStatus ?: 'ERROR'
        this.autoRollbackOnFailure = props.autoRollbackOnFailure ?: true
        this.enableDeployObservability = props.enableDeployObservability ?: false
    }

    Boolean shouldCheckMonitors(ModuleConfig config) {
        !config.skipDeploymentGates
    }

    Boolean shouldDeployObservability(ModuleConfig config) {
        config.jenkins.echo "test here: ${enableDeployObservability}"
        enableDeployObservability
    }

    void observabilityDeploy(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        jenkins.observability.deploy(environment: env)
    }

    Boolean checkMonitors(ModuleConfig config, Boolean isPostDeployment) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def env = config.env

        def passing = true
        def currentAttempt = 0
        def maxRetries = 0
        def waitTimeSeconds = 0
        Boolean isProd = 'prd'.equalsIgnoreCase(env)

        if (isPostDeployment) {
            maxRetries = isProd ? postCheckAttemptsPrd : postCheckAttemptsNonPrd
            waitTimeSeconds = isProd ? postCheckIntervalSecondsPrd : postCheckIntervalSecondsNonPrd
        }

        while (currentAttempt <= maxRetries) {
            if (!workflowSuccess(config, currentAttempt)) {
                passing = false
                break
            }
            if (waitTimeSeconds > 0) {
                jenkins.sleep(waitTimeSeconds)
            }
            currentAttempt++
        }

        if (passing) {
            jenkins.echo("Dynatrace Monitor checks success")
            return true
        }

        def message = "(${projectName}) dynatrace monitor(s) in bad state"
        String failureStatus = isProd ? prdFailureStatus : nonPrdFailureStatus
        if ('ERROR'.equalsIgnoreCase(failureStatus)) {
            if (config.rollback && autoRollbackOnFailure) {
                config.rollback.run()
            }
            jenkins.notifyFailure(projectName, message)
            jenkins.error("Failed app monitor checks")
            jenkins.currentBuild.result = Result.FAILURE.toString()
        } else if ('WARN'.equalsIgnoreCase(failureStatus)) {
            jenkins.echo("Failed app monitor checks")
            jenkins.currentBuild.result = Result.UNSTABLE.toString()
        }
        return false
    }

    Boolean workflowSuccess(ModuleConfig config, int currentAttempt) {
        def jenkins = config.jenkins
        def env = config.env
        def projectName = config.projectName
        dynatraceEndpoint = env == "prd" ? "https://jql50548.apps.dynatrace.com" : "https://dlg34900.apps.dynatrace.com"

        // Step 1: Get the Bearer Token
        accessToken = getBearerToken(config)

        // Step 2: Search for the Workflow by name
        def workflowId = findWorkflowId(jenkins, dynatraceEndpoint, accessToken, projectName, env)

        // Step 3: Run the Workflow
        def executionId = runWorkflow(jenkins, dynatraceEndpoint, accessToken, workflowId, currentAttempt)
        jenkins.sleep(1)

        // Step 4: Wait for the execution to finish
        waitForExecutionSuccess(jenkins, dynatraceEndpoint, accessToken, executionId)

        // Step 5: Check validation result after the execution success
        def tasks = getExecutionTasks(jenkins, dynatraceEndpoint, accessToken, executionId)
        if (siteReliabilityGuardianFailed(tasks) || syntheticWorkflowFailed(tasks)) {
            jenkins.echo "Dynatrace workflow validation failed, check " +
                    "${dynatraceEndpoint}/ui/apps/dynatrace.automations/executions/${executionId}"
            return false
        }

        jenkins.echo "Dynatrace workflow validation success, check " +
                "${dynatraceEndpoint}/ui/apps/dynatrace.automations/executions/${executionId}"
        return true
    }

    @Override
    protected void initializeStages() {
        preCheck = new Stage({ config -> checkMonitors(config, false) }, this.&shouldCheckMonitors)
        postCheck = new Stage({ config -> checkMonitors(config, true) }, this.&shouldCheckMonitors)
        globalDeploy = new Stage(this.&observabilityDeploy, this.&shouldDeployObservability)
    }

    private String getBearerToken(ModuleConfig config) {
        def jenkins = config.jenkins
        if (secrets == null) {
            secrets = getSecrets(config)
        }
        def clientId = secrets.client_id
        def clientSecret = secrets.client_secret
        def scope = java.net.URLEncoder.encode("automation:workflows:run automation:workflows:read storage:bizevents:read", "UTF-8")
        def tokenResponse = jenkins.sh(
                script: """
                set +x
                curl --silent --location 'https://sso.dynatrace.com/sso/oauth2/token' \\
                --header 'Content-Type: application/x-www-form-urlencoded' \\
                --data "grant_type=client_credentials&client_id=${clientId}&client_secret=${clientSecret}&scope=${scope}"
                """,
                returnStdout: true,
                ).trim()
        def tokenJson = jenkins.readJSON text: tokenResponse
        def accessToken = tokenJson.access_token
        if (!accessToken) {
            jenkins.error "Failed to retrieve Dynatrace Bearer Token!"
            return null
        }
        jenkins.echo "Dynatrace Bearer Token retrieved successfully."
        return accessToken
    }

    private String findWorkflowId(def jenkins, String dynatraceEndpoint, String accessToken,
            String projectName, String env) {
        def encodeName = java.net.URLEncoder.encode("${projectName}", "UTF-8")
        def encodeEnv = java.net.URLEncoder.encode("${env}", "UTF-8")
        def workflowUrl = "${dynatraceEndpoint}/platform/automation/v1/workflows?" +
                "search=${encodeName}%20AND%20${encodeEnv}"
        def workflowResponse = jenkins.sh(
                script: """
                        set +x
                        curl --silent -X GET "${workflowUrl}" \\
                             -H "Authorization: Bearer ${accessToken}" \\
                             -H "Accept: application/json"
                        """,
                returnStdout: true,
                ).trim()

        def apiResponseJson = jenkins.readJSON text: workflowResponse
        if (!apiResponseJson.results || apiResponseJson.results.size() == 0
                || !apiResponseJson.results[0].id) {
            jenkins.error "Dynatrace Workflow for project '${projectName}' in environment '${env}' not found"
            return null
        }
        return apiResponseJson.results[0].id
    }

    private String runWorkflow(def jenkins, String dynatraceEndpoint, String accessToken,
            String workflowId, int currentAttempt) {
        def response = jenkins.sh(
                script: """
                set +x
                curl --silent -X POST "${dynatraceEndpoint}/platform/automation/v1/workflows/${workflowId}/run" \\
                -H "Authorization: Bearer ${accessToken}" \\
                -H "Content-Type: application/json" \\
                -d '{"params": {"currentAttempt": ${currentAttempt}}}'
            """,
                returnStdout: true,
                ).trim()

        def executionId = jenkins.readJSON(text: response).id
        if (!executionId) {
            jenkins.error "Failed to run Dynatrace workflow ${workflowId}!"
        }
        return executionId
    }

    private void waitForExecutionSuccess(def jenkins, String dynatraceEndpoint, String accessToken, String executionId) {
        def executionState = null
        int waitTime = 5
        def maxRetries = 20
        int currentTry = 0
        def executionUrl="${dynatraceEndpoint}/platform/automation/v1/executions/${executionId}"
        while (currentTry < maxRetries) {
            def executionResponse = jenkins.sh(
                    script: """
                set +x
                curl --silent -X GET "${executionUrl}" -H "Authorization: Bearer ${accessToken}" -H "Content-Type: application/json"
                """,
                    returnStdout: true,
                    ).trim()

            executionState = jenkins.readJSON(text: executionResponse).state
            if (executionState != null && !"RUNNING".equalsIgnoreCase(executionState)) {
                break
            } else {
                jenkins.sleep(waitTime)
                currentTry++
            }
        }
        if (executionState == null || !"SUCCESS".equalsIgnoreCase(executionState)) {
            jenkins.error "Dynatrace workflow execution failed or timeout, " +
                    "${dynatraceEndpoint}/ui/apps/dynatrace.automations/executions/${executionId}"
        }
    }

    private def getExecutionTasks(def jenkins, String dynatraceEndpoint, String accessToken, String executionId) {
        def taskUrl="${dynatraceEndpoint}/platform/automation/v1/executions/${executionId}/tasks"
        def taskResponse = jenkins.sh(
                script: """
                set +x
                curl --silent -X GET "${taskUrl}" -H "Authorization: Bearer ${accessToken}" -H "Content-Type: application/json"
                """,
                returnStdout: true,
                ).trim()
        return jenkins.readJSON(text: taskResponse)
    }

    private boolean siteReliabilityGuardianFailed(def tasks) {
        def result = false
        tasks.each { taskId, taskInfo ->
            if (taskInfo.result && taskInfo.result.validation_status) {
                String status = taskInfo.result.validation_status
                if ("fail".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                    result = true
                }
            }
        }
        return result
    }

    private boolean syntheticWorkflowFailed(def tasks) {
        def result = false
        tasks.each { taskId, taskInfo ->
            if (taskInfo.result && taskInfo.result["batch.status"]) {
                String status = taskInfo.result["batch.status"]
                if ("fail".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                    result = true
                }
            }
        }
        return result
    }

    private Map getSecrets(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env

        jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS['shd']}:role/CHEWY-cross-jenkins") {
            def secretName
            if ("prd".equalsIgnoreCase(env)) {
                secretName = "dynatrace-terraform/cicdsrgexec/prod-us-app-cicd-oath"
            } else {
                secretName = "dynatrace-terraform/cicdsrgexec/nonprod-us-app-cicd-oath"
            }
            try {
                return jenkins.readJSON(text: jenkins.secretsmanager.getKVSecret(secretName))
            } catch (Exception ex) {
                jenkins.error("Failed to get dynatrace oauth client secret from ${secretName}")
                throw ex
            }
        }
    }
}
