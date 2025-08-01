package pipelines.stages

import hudson.model.Result
import pipelines.PipelineConfig
import pipelines.metadata.Deployment
import pipelines.metadata.DeploymentID
import pipelines.metadata.DeploymentMetadata
import pipelines.metadata.S3Util
import pipelines.metadata.Validation
import pipelines.module.Module
import pipelines.module.ModuleConfig

import java.time.LocalDateTime

class DeployModules {
    PipelineConfig pipelineConfig
    def jenkins
    String projectName
    String projectVersion
    String previousVersion
    List<Module> modules
    String env
    List<String> regions
    List<String> previousEnvs
    String notificationColor = "blue"
    CRModules crModule
    S3Util s3Util

    // Options
    Boolean isAutomatedDeploy
    Boolean isBranchDeploy
    Boolean skipDeploymentGates
    Boolean uninstall

    DeployModules(PipelineConfig pipelineConfig,
    String projectVersion,
    String env) {
        this.pipelineConfig = pipelineConfig
        this.jenkins = pipelineConfig.jenkins
        this.projectName = pipelineConfig.projectName
        this.modules = pipelineConfig.modules
        this.projectVersion = projectVersion
        this.env = env
        this.regions = pipelineConfig.regions
        this.previousVersion = null
        this.s3Util = new S3Util(jenkins, pipelineConfig.deploymentMetadataBucket)
    }

    DeployModules(PipelineConfig pipelineConfig,
    String projectVersion,
    List<Module> modules,
    String env) {
        this.pipelineConfig = pipelineConfig
        this.jenkins = pipelineConfig.jenkins
        this.projectName = pipelineConfig.projectName
        this.modules = modules ?: pipelineConfig.modules
        this.projectVersion = projectVersion
        this.env = env
        this.regions = pipelineConfig.regions
        this.previousVersion = null
        this.previousEnvs = new ArrayList<>()
    }

    void run(Map options) {
        jenkins.echo("Deploying modules: $modules to $env")

        isBranchDeploy = (options && options.isBranchDeploy != null) ? options.isBranchDeploy : false
        skipDeploymentGates = (options && options.skipDeploymentGates != null) ? options.skipDeploymentGates : false
        isAutomatedDeploy = (options && options.isAutomatedDeploy != null) ? options.isAutomatedDeploy : false
        crModule = (options && options.CRModule) ? options.CRModule as CRModules : null
        uninstall = (options && options.uninstall != null) ? options.uninstall : false
        previousEnvs = (options && options.deploymentLowerEnvs)
                ? S3Util.getPreviousDeployedEnvs(options.deploymentLowerEnvs as List<List<String>>, env)
                : S3Util.getPreviousDeployedEnvs(pipelineConfig.deploymentLowerEnvironments, env)


        // Branch deploy to only the first region
        if (isBranchDeploy) {
            regions = [regions.first()]
        }

        ModuleConfig moduleConfig = ModuleConfig.builder()
                .pipelineConfig(pipelineConfig)
                .projectVersion(projectVersion)
                .isBranchDeploy(isBranchDeploy)
                .skipDeploymentGates(skipDeploymentGates)
                .isAutomatedDeploy(isAutomatedDeploy)
                .env(env)
                .uninstall(uninstall)
                .build()

        if (!isBranchDeploy && !skipDeploymentGates) {
            jenkins.echo("Checking Metadata file for previous envs: ${previousEnvs}")
            for (String prevEnv: previousEnvs) {
                if (!prevEnv.isEmpty() && !s3Util.validateSuccessfulDeploymentMetadata(pipelineConfig, projectVersion, prevEnv)) {
                    jenkins.currentBuild.result = Result.FAILURE.toString()
                    jenkins.error("Aborting deployment for ${env}: Metadata file for previous env ${prevEnv} ${projectVersion} deployment failed," +
                            " please rerun ${prevEnv} ${projectVersion}, or check SKIP_DEPLOY_GATES/SKIP_MONITORS by the manual deployment")
                    return
                }
            }
        }

        preCheck(moduleConfig)
        if (env == "prd") {
            startCR(moduleConfig)
        }
        globalDeploy(moduleConfig)
        regionDeploy(moduleConfig)
        postCheck(moduleConfig)
        regionPostDeploy(moduleConfig)
        publishMetadata(moduleConfig)
    }

    private static String generateTimestamp() {
        String.format('%tF %<tH:%<tM', LocalDateTime.now())
    }

    private void preCheck(ModuleConfig config) {
        boolean shouldRespectChangeFreezesProd = pipelineConfig.respectChangeFreezesProd && env == "prd"
        boolean shouldRespectChangeFreezesNonprod = pipelineConfig.respectChangeFreezesNonprod && env == "stg"
        if (!config.skipDeploymentGates && jenkins.params.RESPECT_CHANGE_FREEZE && (shouldRespectChangeFreezesProd || shouldRespectChangeFreezesNonprod)) {
            jenkins.echo("Checking for change freeze in ${env}")
            if (jenkins.boreas.checkChangeFreezeExists(env)) {
                jenkins.notify(projectName, "Skipping deployment (change freeze in effect or imminent)", notificationColor);
                jenkins.currentBuild.result = Result.NOT_BUILT.toString()
                jenkins.error('Stopping the build with result: ' + jenkins.currentBuild.result)
            }
        }
        if (crModule != null && crModule.shouldSkipDeploy()) {
            jenkins.notify(projectName, "Skipping deployment", notificationColor);
            jenkins.currentBuild.result = Result.NOT_BUILT.toString()
            jenkins.error('Stopping the build with result: ' + jenkins.currentBuild.result)
        }

        def stages = [:]
        def s3Util = new S3Util(jenkins, config.metadataBucket)
        DeploymentID id = new DeploymentID([
            app: projectName,
            environment: env,])
        this.previousVersion = s3Util.getLatestVersion(id)
        jenkins.echo("Previous version: ${this.previousVersion}")

        modules.each {
            it.preCheck.addStage(stages, config, "Pre Check", it.name)
        }

        jenkins.stage("Pre Check: ${env}") {
            jenkins.parallel stages
        }
    }

    private void startCR(ModuleConfig config) {
        jenkins.stage("Start CR: ${env}") {
            config.projectVersion = crModule.run()
        }
    }

    private void globalDeploy(ModuleConfig config) {
        def stages = [:]

        modules.each {
            if (it.name != "flyway") {
                it.globalDeploy.addStage(stages, config, "Global Deploy ${env}", it.name)
            }
        }

        jenkins.stage("Global Deploy: ${env}") {
            jenkins.parallel stages
        }

        // Manually execute flyway after all other global modules
        def flywayModule = modules.find { it.name == "flyway" }
        if (flywayModule) {
            jenkins.echo("Flyway module exists... deploying now")
            def flywayStage = [:]
            flywayModule.globalDeploy.addStage(flywayStage, config, "Global Deploy: ${env}", flywayModule.name)
            jenkins.stage("Global Deploy: ${env} - post terraform") {
                flywayStage.each { stageName, stageClosure ->
                    stageClosure()
                }
            }
        }
    }

    private void regionDeploy(ModuleConfig moduleConfig) {
        def stages = [:]
        regions.each { region ->
            ModuleConfig regionConfig = new ModuleConfig(moduleConfig, region)

            modules.each {
                it.regionDeploy.addStage(stages, regionConfig, "Regional Deploy ${region}", it.name)
            }
        }

        jenkins.stage("Regional Deploy: ${env}") {
            jenkins.parallel stages
        }
    }

    private void postCheck(ModuleConfig config) {
        def stages = [:]
        ModuleConfig postDeployConfig = new ModuleConfig(config,
                new RollbackModules(pipelineConfig, this.previousVersion, env, regions))

        if (config.skipDeploymentGates) {
            jenkins.echo("Skipping post check for ${env}: skipDeploymentGates flag is on")
        } else {
            modules.each {
                it.postCheck.addStage(stages, postDeployConfig, "Post Check", it.name)
            }
            jenkins.stage("Post Check: ${env}") {
                jenkins.parallel stages
            }
        }
    }

    private void regionPostDeploy(ModuleConfig moduleConfig) {
        def stages = [:]
        regions.each { region ->
            ModuleConfig regionConfig = new ModuleConfig(moduleConfig, region)

            modules.each {
                it.regionPostDeploy.addStage(stages, regionConfig, "Regional Post Deploy ${region}", it.name)
            }
        }

        jenkins.stage("Regional Post Deploy: ${env}") {
            jenkins.parallel stages
        }
    }

    private void publishMetadata(ModuleConfig config) {
        def s3Util = new S3Util(jenkins, config.metadataBucket)
        Validation validation = new Validation([success:modules.collect { it.name }])
        Deployment deployment = new Deployment([
            success:true,
            currentVersion:projectVersion,
            rollbackVersion:previousVersion,])
        DeploymentMetadata deploymentMetadata = new DeploymentMetadata([
            timestamp:generateTimestamp(),
            jenkinsLink:config.jenkins.env.BUILD_URL,
            deployment:deployment,
            validation:validation,])

        regions.each { region ->
            String simplifiedVersion = projectVersion
            if (projectVersion.startsWith("v")) {
                simplifiedVersion = projectVersion.substring(1)
            }

            DeploymentID id = new DeploymentID([
                app: projectName,
                version: simplifiedVersion,
                environment: env,
                shortRegion: region],)
            s3Util.uploadMetadata(id, deploymentMetadata)
            if (!isBranchDeploy) {
                s3Util.updateLatestVersion(id)
            }
        }
    }
}
