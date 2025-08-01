package pipelines

import pipelines.module.DockerModule
import pipelines.module.AptHelmModule
import pipelines.module.ChewyCommonsModule
import pipelines.module.ClosureModule
import pipelines.module.DynatraceMonitorModule
import pipelines.module.E2EModule
import pipelines.module.GatlingModule
import pipelines.module.HelmChartModule
import pipelines.module.HelmModule
import pipelines.module.LambdaModule
import pipelines.module.LibraryModule
import pipelines.module.MakefileProjectModule
import pipelines.module.PlaywrightModule
import pipelines.module.ReactModule
import pipelines.post.GlobalPost
import pipelines.stages.BuildModules
import pipelines.stages.DeployModules
import pipelines.stages.PublishModules
import pipelines.stages.ValidateModules

class BranchPipeline {
    PipelineConfig pipelineConfig
    String projectVersion
    def jenkins
    String projectName

    BranchPipeline(PipelineConfig pipelineConfig, String projectVersion) {
        this.pipelineConfig = pipelineConfig
        this.projectVersion = projectVersion
        this.jenkins = pipelineConfig.jenkins
        this.projectName = pipelineConfig.projectName
    }

    void run(boolean isMergeQueue) {
        def postStep = new GlobalPost(pipelineConfig.postParams, jenkins, projectName, true, isMergeQueue)
        try {
            // build everything
            new BuildModules(pipelineConfig, this.projectVersion, true).run()

            // validate everything
            new ValidateModules(pipelineConfig, this.projectVersion, true).run()

            // publish app, lambdas, and library if present
            def dockerModules = findAllModules(DockerModule)
            def lambdaModule = findAllModules(LambdaModule)
            def customClosureModule = findAllModules(ClosureModule) // should be included in every stage
            def libraryModule = findAllModules(LibraryModule)
            def reactModule = findAllModules(ReactModule)
            def makefileProjectModule = findAllModules(MakefileProjectModule)
            def chewyCommonsModule = findAllModules(ChewyCommonsModule)
            def helmChartModule = findAllModules(HelmChartModule)

            def publishModules = dockerModules + lambdaModule  + customClosureModule + libraryModule + reactModule + makefileProjectModule + helmChartModule+ chewyCommonsModule
            new PublishModules(pipelineConfig, projectName, this.projectVersion, null, publishModules).run(
                    isBranchDeploy: true
                    )
            def helmModule = findAllModules(HelmModule)
            def aptHelmModule = findAllModules(AptHelmModule)
            def functionalTestModules = findAllModules([
                E2EModule,
                PlaywrightModule,
                DynatraceMonitorModule,
            ])
            def performanceTestModules = findAllModules(GatlingModule)
            def branchDeployModules = helmModule + aptHelmModule + customClosureModule + functionalTestModules + performanceTestModules + reactModule + helmChartModule

            def deploymentEnvs = pipelineConfig.getBranchDeploymentEnvs(isMergeQueue)
            def envInParallel = deploymentEnvs.collect{ [it] }
            Boolean skipDeploymentGates = jenkins.params.SKIP_DEPLOY_GATES == null ? true : jenkins.params.SKIP_DEPLOY_GATES
            List deployedEnvs = []
            for (String env : deploymentEnvs) {
                new DeployModules(pipelineConfig, this.projectVersion, branchDeployModules, env).run([
                    isBranchDeploy     : true,
                    // merge queue should not skip deployment gates
                    skipDeploymentGates: skipDeploymentGates && !isMergeQueue,
                    uninstall: isMergeQueue,
                    deploymentLowerEnvs: envInParallel,
                ])

                // Set the displayName of the current build (example: 123 - My Project:1.23.4 -> dev,qat,stg)
                deployedEnvs.add(env)
                jenkins.currentBuild.displayName = "${jenkins.currentBuild.number} - ${projectName}:${projectVersion} -> ${deployedEnvs.join(',')}"
            }

            postStep.success("Branch Pipeline Finished Successfully", false)
        } catch (Exception ex) {
            postStep.failure()
            throw ex
        } finally {
            postStep.always()
        }
    }

    List<Module> findAllModules(Class type) {
        return pipelineConfig.modules.findAll { module -> type.isInstance(module) } as List<Module>
    }

    List<Module> findAllModules(List<Class> types) {
        return pipelineConfig.modules.findAll { module ->
            types.any { type -> type.isInstance(module)}
        } as List<Module>
    }
}
