package pipelines

import pipelines.post.GlobalPost
import pipelines.stages.CRModules
import pipelines.stages.DeployModules
import pipelines.stages.RollbackModules

class ProdPipeline {
    PipelineConfig pipelineConfig
    def jenkins
    String projectName
    Boolean rollback = false
    String notificationColor = 'blue'

    ProdPipeline(PipelineConfig pipelineConfig) {
        this.pipelineConfig = pipelineConfig
        this.jenkins = pipelineConfig.jenkins
        this.projectName = pipelineConfig.projectName
        this.rollback = pipelineConfig.rollback
    }

    void run() {
        def postStep = new GlobalPost(pipelineConfig.postParams, jenkins, projectName)
        jenkins.env.DEPLOY_STEP = 'Start prod pipeline'

        if(pipelineConfig.sendLowerEnvironmentSuccess()) {
            jenkins.notify(projectName, "Prod Pipeline Starting (<${jenkins.env.BUILD_URL}|Jenkins>)", notificationColor)
        }

        Boolean isAutomatedDeploy = jenkins.params.TAG == PipelineConfig.LATEST_TAG
        Boolean skipDeploymentGates = jenkins.params.SKIP_MONITORS || jenkins.params.SKIP_DEPLOY_GATES ?: false
        try {
            def CRModule = new CRModules(
                    pipelineConfig,
                    rollback,
                    isAutomatedDeploy,
                    )
            String projectVersion = CRModule.getTag()
            if (rollback) {
                CRModule.run()
                new RollbackModules(pipelineConfig, projectVersion, 'prd', pipelineConfig.regions).run()
            } else {
                // Set the displayName of the current build (example: 123 - My Project:1.23.4)
                jenkins.currentBuild.displayName = "${jenkins.currentBuild.number} - ${projectName}:${projectVersion}"

                new DeployModules(pipelineConfig, projectVersion, 'prd').run([
                    CRModule: CRModule,
                    isAutomatedDeploy: isAutomatedDeploy,
                    skipDeploymentGates: skipDeploymentGates,
                ])
            }
            CRModule.closeCR(true)

            postStep.success('Prod Pipeline Finished Successfully', true)
        } catch (Exception ex){
            postStep.failure()
            throw ex
        } finally {
            postStep.always()
        }
    }
}
