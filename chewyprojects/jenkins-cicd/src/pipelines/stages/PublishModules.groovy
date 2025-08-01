package pipelines.stages

import pipelines.module.Module
import pipelines.module.ModuleConfig

class PublishModules {
    def jenkins
    String projectName
    String projectVersion
    String previousVersion = null
    String vertical
    List<Module> modules

    PublishModules(def config, String projectName, String projectVersion, String previousVersion, List<Module> modules) {
        this.jenkins = config.jenkins
        this.projectName = projectName
        this.projectVersion = projectVersion
        this.previousVersion = previousVersion
        this.vertical = config.vertical
        this.modules = modules
    }

    void run(Map options) {
        jenkins.echo("Publishing modules: $modules with version $projectVersion")

        Boolean isBranchDeploy = (options && options.isBranchDeploy != null) ? options.isBranchDeploy : false
        ModuleConfig config = ModuleConfig.builder()
                .jenkins(jenkins)
                .projectName(projectName)
                .projectVersion(projectVersion)
                .vertical(vertical)
                .isBranchDeploy(isBranchDeploy)
                .previousVersion(previousVersion)
                .build()

        def stages = [:]
        modules.each {it.publish.addStage(stages, config, "Publish", it.name)}

        jenkins.stage("Publish modules") {
            jenkins.parallel stages
        }
    }
}
