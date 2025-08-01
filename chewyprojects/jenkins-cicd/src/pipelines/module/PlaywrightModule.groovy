package pipelines.module

import hudson.model.Result
import pipelines.PipelineConfig
import pipelines.PlaywrightModuleConfig
import pipelines.metadata.CommonUtil

class PlaywrightModule extends Module {
    private static boolean setupExecuted = false

    def e2eSecrets = [:]
    def testSuites
    String testTargetName
    def secretKeys
    def envMap
    def playwrightImage
    Boolean autoRollbackOnFailure
    Boolean runInPRBuild
    String prdFailureStatus
    String nonPrdFailureStatus


    PlaywrightModule(PlaywrightModuleConfig playwrightModuleConfig) {
        super([name: "playwright ${playwrightModuleConfig.name}"])

        this.testSuites = playwrightModuleConfig.testSuites
        this.testTargetName = playwrightModuleConfig.testTargetName
        this.secretKeys = playwrightModuleConfig.secretKeys
        this.envMap = playwrightModuleConfig.envMap
        this.playwrightImage = playwrightModuleConfig.playwrightImage
        this.autoRollbackOnFailure = playwrightModuleConfig.autoRollbackOnFailure
        this.runInPRBuild = playwrightModuleConfig.runInPRBuild
        this.nonPrdFailureStatus = playwrightModuleConfig.nonPrdFailureStatus
        this.prdFailureStatus = playwrightModuleConfig.prdFailureStatus
    }

    PlaywrightModule(PipelineConfig config, props) {
        super(props + [name: "playwright ${props.name}"])
        this.testSuites = props.testSuites
        this.testTargetName = props.testTargetName
        this.secretKeys = props.secretKeys
        this.envMap = props.envMap
        this.playwrightImage = props.playwrightImage
        this.autoRollbackOnFailure = props.autoRollbackOnFailure
        this.runInPRBuild = props.runInPRBuild
        this.nonPrdFailureStatus = props.nonPrdFailureStatus
        this.prdFailureStatus = props.prdFailureStatus
    }

    Boolean shouldRunTests(ModuleConfig config) {
        if (config.isPrBuild) {
            return runInPRBuild
        }
        return !config.skipDeploymentGates
    }

    Boolean runPlaywright(ModuleConfig config) {
        setupPlaywright(config)

        def isBranchDeploy = config.isBranchDeploy
        def projectName = config.projectName
        def jenkins = config.jenkins
        def env = config.env
        def region = config.region
        def account_id = jenkins.ACCOUNTS[env]
        def namespace = config.namespace
        // Assumes this module will always be used in `apt-shared` cluster
        def clusterName = 'apt-shared'
        if (isBranchDeploy) {
            namespace = jenkins.k8s.generateNamespaceName()
        }
        def cluster = "${env}-${jenkins.REGIONS[region]}-${clusterName}"
        jenkins.env.DEPLOY_STEP = "Playwright test ${name} - ${env}"
        String previewUrl = CommonUtil.getUrl(jenkins, account_id, region, cluster, projectName, namespace, env)
        String e2eTestEnv = isBranchDeploy ? env + '-fb' : env

        def playwrightEnv = []
        for(String key : e2eSecrets.keySet()) {
            playwrightEnv.add("${key}=${e2eSecrets[key]}")
        }

        if (envMap != null && !envMap.isEmpty()) {
            for (String key : envMap.keySet()) {
                playwrightEnv.add("${key}=${envMap[key]}")
            }
        }

        String proxyBasePath = CommonUtil.getProxyBasePath(config)
        if(!proxyBasePath.isEmpty()) {
            playwrightEnv.add("PROXY_BASE_PATH=${proxyBasePath}")
        }

        playwrightEnv.add("PLAYWRIGHT_ENVIRONMENT=${env}")
        playwrightEnv.add("PW_FB_URL=${previewUrl}")
        playwrightEnv.add("TEST_BE_ENVIRONMENT=${e2eTestEnv}")
        playwrightEnv.add("TEST_FE_ENVIRONMENT=${e2eTestEnv}")

        def isProdSuite = testSuites.any { it.contains('prod') }
        def isBackendSuite = testSuites.any { it.contains('backend') }

        try {
            if (isProdSuite) {
                if (env == 'prd') {
                    if (isBackendSuite) {
                        runBackendTest(config, playwrightEnv, previewUrl, testTargetName)
                    } else {
                        runFrontendTest(config, playwrightEnv)
                    }
                }
            } else {
                if (env != 'prd') {
                    if (isBackendSuite) {
                        runBackendTest(config, playwrightEnv, previewUrl, testTargetName)
                    } else {
                        runFrontendTest(config, playwrightEnv)
                    }
                }
            }
            return true
        } catch (Exception ex) {
            if ("prd".equalsIgnoreCase(jenkins.env.ENVIRONMENT)) {
                if ('ERROR'.equalsIgnoreCase(prdFailureStatus)) {
                    // TODO: refactor rollback up to the Pipeline level easier so it's only ever handled once.
                    if (config.rollback && autoRollbackOnFailure) {
                        config.rollback.run()
                    }
                    jenkins.notifyFailure(projectName, "Playwright test ${testSuites.join(', ')} failed in prd")
                    jenkins.error("Start to rollback due to Playwright test failure, ${ex.getMessage()}")
                    jenkins.currentBuild.result = Result.FAILURE.toString()
                    return false
                } else if ('WARN'.equalsIgnoreCase(prdFailureStatus)) {
                    jenkins.echo("Playwright test failed in prd, ${ex.getMessage()}")
                    jenkins.currentBuild.result = Result.UNSTABLE.toString()
                    return true
                }
            } else {
                if ('ERROR'.equalsIgnoreCase(nonPrdFailureStatus)) {
                    jenkins.error("Playwright test failed for ${testSuites.join(', ')} - ${env}, ${ex.getMessage()}")
                    jenkins.currentBuild.result = Result.FAILURE.toString()
                    return false
                } else if ('WARN'.equalsIgnoreCase(nonPrdFailureStatus)) {
                    jenkins.echo("Playwright test failed for ${testSuites.join(', ')} - ${env}, ${ex.getMessage()}")
                    jenkins.currentBuild.result = Result.UNSTABLE.toString()
                    return true
                }
            }
        } finally {
            try {
                if (jenkins.fileExists("allure-results") &&
                        jenkins.sh(script: "ls -A allure-results", returnStatus: true) == 0) {
                    jenkins.sh("npx allure generate --single-file allure-results --clean")
                } else {
                    jenkins.echo("No test results found, skipping allure report generation.")
                }
            } catch (Exception e) {
                jenkins.echo("Skipping allure report due to no available test results, ${e}")
            }
        }
    }

    @Override
    protected void initializeStages() {
        postCheck = new Stage(this.&runPlaywright, this.&shouldRunTests)
    }

    static private void installDependencies(ModuleConfig config) {
        def jenkins = config.jenkins
        jenkins.configFileProvider([
            jenkins.configFile(fileId: 'npmrc-default', targetLocation: './.npmrc')
        ]) {
            jenkins.sh "${jenkins.ecrLogin()}"
            jenkins.sh "npm config fix"
            jenkins.sh """
                #!/bin/bash
                ([[ -f .env ]] || touch .env) && \
                CI=true npm i
               """
        }
        setupExecuted = true
    }

    private void setupPlaywright(ModuleConfig config) {
        // Only execute if not already done
        if (!setupExecuted) {
            config.jenkins.lock('playwright-setup') {
                if (!setupExecuted) {
                    installDependencies(config)
                }
            }
        }
        fetchSecrets(config)
    }

    private String getJenkinsDockerArgs(ModuleConfig config) {
        def jenkins = config.jenkins
        String dockerClientPath = jenkins.sh(script: 'which docker', returnStdout: true).trim()
        String dockerComposePath = jenkins.sh(script: 'which docker-compose', returnStdout: true).trim()
        String dockerSocketGroupId = jenkins.sh(script: 'stat -c %g /var/run/docker.sock', returnStdout: true).trim()

        return "-v ${jenkins.env.HOME}:${jenkins.env.HOME} " +
                "-v ${jenkins.env.HOME}/.ssh:/home/jenkins/.ssh " +
                "-e HOME=${jenkins.env.HOME} " +
                "-e GRADLE_USER_HOME=${jenkins.env.HOME}/.gradle " +
                "-e SONAR_USER_HOME=${jenkins.env.HOME}/.sonar " +
                "-v /var/run/docker.sock:/var/run/docker.sock " +
                "-v ${dockerClientPath}:/usr/bin/docker " +
                "-v ${dockerComposePath}:/usr/local/bin/docker-compose " +
                "--group-add ${dockerSocketGroupId}"
    }

    private void runTestInDocker(ModuleConfig config, List<String> playwrightEnv, String command) {
        def jenkins = config.jenkins
        def env = config.env
        def account_id = jenkins.ACCOUNTS[env]
        def playwrightRunArgs = getJenkinsDockerArgs(config) + " -u root:root"

        jenkins.withAWS(role: "arn:aws:iam::${account_id}:role/CHEWY-cross-jenkins") {
            jenkins.docker.image(playwrightImage).inside(playwrightRunArgs) {
                jenkins.sh "${jenkins.ecrLogin()}"
                jenkins.sh "npm config fix"
                jenkins.withEnv(playwrightEnv) {
                    jenkins.sh command
                }
            }
        }
    }

    private void runBackendTest(ModuleConfig config, List<String> playwrightEnv, String previewUrl, String testTargetName) {
        // Execute the backend-specific docker command
        String backendCommand = """
        sleep 45 && PLAYWRIGHT_HTML_REPORT='playwright-report-${testSuites.get(0)}/' \
        npx playwright test --project="${testSuites.get(0)}"
    """
        runTestInDocker(config, playwrightEnv, backendCommand)

        // Execute additional gradle task for backend tests
        def jenkins = config.jenkins
        def env = config.env
        def account_id = jenkins.ACCOUNTS[env]
        jenkins.withAWS(role: "arn:aws:iam::${account_id}:role/CHEWY-cross-jenkins") {
            jenkins.sh """
            PW_FB_URL=${previewUrl} \
            CHEWY_ENVIRONMENT_NAME=${env} \
            PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
            ./gradlew ${testTargetName} --fail-fast -x check
        """
        }
    }

    private void fetchSecrets(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def account_id = jenkins.ACCOUNTS[env]
        def region = config.region
        def projectName = config.projectName

        jenkins.configFileProvider([
            jenkins.configFile(fileId: 'npmrc-default', targetLocation: './.npmrc')
        ]) {
            jenkins.sh "${jenkins.ecrLogin()}"

            jenkins.withAWS(role: "arn:aws:iam::${account_id}:role/CHEWY-cross-jenkins") {
                def secretName = "/chewy/${env}/${region}/${projectName}/secrets"

                for(String key: secretKeys) {
                    try {
                        e2eSecrets[key] = jenkins.secretsmanager.getKVSecret(
                                secretName,
                                key,
                                region)
                    } catch (Exception ex) {
                        jenkins.echo("Failed to get secret ${key} from ${secretName}")
                        throw ex
                    }
                }
            }
        }
    }

    private void runFrontendTest(ModuleConfig config, List<String> playwrightEnv) {
        // Execute the frontend-specific docker command
        String frontendCommand = """
        sleep 45 && PLAYWRIGHT_HTML_REPORT='playwright-report-${testSuites.get(0)}/html/' \
        npx playwright test \
        --output="playwright-report-${testSuites.get(0)}/output/" \
        --project="${testSuites.get(0)}"
    """
        runTestInDocker(config, playwrightEnv, frontendCommand)
    }
}
