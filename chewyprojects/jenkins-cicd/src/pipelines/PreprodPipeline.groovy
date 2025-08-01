package pipelines

import hudson.model.Result
import pipelines.metadata.CommonUtil
import pipelines.module.Module
import pipelines.post.GlobalPost
import pipelines.stages.RollbackModules
import pipelines.stages.ValidateModules
import pipelines.stages.BuildModules
import pipelines.stages.PublishModules
import pipelines.stages.DeployModules

class PreprodPipeline {
    PipelineConfig pipelineConfig
    def jenkins
    String projectName
    String projectVersion
    List<Module> modules

    // Internal
    String notificationColor = "green"

    PreprodPipeline(PipelineConfig pipelineConfig, String projectVersion) {
        this.pipelineConfig = pipelineConfig
        this.projectVersion = projectVersion
        this.jenkins = pipelineConfig.jenkins
        this.projectName = pipelineConfig.projectName
        this.modules = pipelineConfig.modules
    }

    void run(boolean skipDeploymentParameters = false) {
        def postStep = new GlobalPost(pipelineConfig.postParams, jenkins, projectName, false)
        // TODO: Perform the deployments in a loop over environment configuration

        jenkins.env.DEPLOY_STEP = "Start preprod pipeline"

        if (pipelineConfig.sendLowerEnvironmentSuccess()) {
            if (pipelineConfig.environment != PipelineConfig.CHOOSE_ALL_SELECT_OPTION) {
                jenkins.notify(projectName, "${pipelineConfig.environment} Pipeline Starting (<${jenkins.env.BUILD_URL}|Jenkins>)", notificationColor)
            } else {
                jenkins.notify(projectName, "Pre-Prod Pipeline Starting (<${jenkins.env.BUILD_URL}|Jenkins>)", notificationColor)
            }
        }

        try {
            // TODO: determine previousVersion
            jenkins.echo("Preprod Pipeline")
            // do not start if rollback is true
            if (pipelineConfig.rollback && pipelineConfig.environment == PipelineConfig.CHOOSE_ALL_SELECT_OPTION) {
                jenkins.notify(projectName, "Skipping deployment (preprod cannot rollback ALL environments)", "blue");
                jenkins.currentBuild.result = Result.NOT_BUILT.toString()
                jenkins.error('Stopping the build with result: ' + jenkins.currentBuild.result)
            }
            def previousVersion = null
            if (pipelineConfig.rollback && pipelineConfig.environment != PipelineConfig.CHOOSE_ALL_SELECT_OPTION) {
                new RollbackModules(pipelineConfig, CommonUtil.getRollbackVersion(jenkins, projectName, pipelineConfig.namespace, pipelineConfig.environment), pipelineConfig.environment, pipelineConfig.regions).run()
            } else {
                new BuildModules(pipelineConfig, this.projectVersion).run()

                // Validate everything
                new ValidateModules(pipelineConfig, this.projectVersion, false).run()

                // Publish snapshot
                new PublishModules(pipelineConfig, projectName, this.projectVersion, previousVersion, modules).run()
                /*
                 run deployments in parallel for each concurrent env sub-list
                 e.g. [ [ "dev", "qa" ], [ "staging" ] ] will deploy dev and qa in parallel, then staging
                 "first" refers to the first environment(s) who do not need to validate metadata
                 */
                List deployedEnvs = []
                pipelineConfig.getPreProdDeploymentEnvs().each { List<String> envs ->
                    // TODO: determine if parallelism is possible
                    // TODO: validate previous versions and apply previousEnvs logic here if necessary
                    envs.each({ env ->
                        new DeployModules(pipelineConfig, this.projectVersion, env).run([
                            skipDeploymentGates: skipDeploymentParameters ||
                            pipelineConfig.skipDeploymentGatesLowerEnvs.contains(env) ||
                            jenkins.params.SKIP_DEPLOY_GATES,
                            isBranchDeploy     : false,
                        ])
                        // Set the displayName of the current build (example: 123 - My Project:1.23.4 -> dev,qat,stg)
                        deployedEnvs.add(env)
                        jenkins.currentBuild.displayName = "${jenkins.currentBuild.number} - ${projectName}:${projectVersion} -> ${deployedEnvs.join(',')}"
                    })
                }
            }

            postStep.success("Pre-Prod Pipeline Finished Successfully",
                    pipelineConfig.sendLowerEnvironmentSuccess())
        } catch (Exception ex) {
            postStep.failure()
            throw ex
        } finally {
            postStep.always()
        }
    }
}
