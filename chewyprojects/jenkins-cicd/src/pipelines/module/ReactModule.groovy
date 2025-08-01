package pipelines.module

import pipelines.module.stage.DockerBuildStage
import pipelines.module.stage.KubernetesRegionDeployStage
import pipelines.module.stage.KubernetesRegionRollbackStage
import pipelines.module.stage.PublishDockerStage
import pipelines.module.stage.SonarqubeStage

class ReactModule extends Module {
    protected Boolean standalone

    ReactModule(String name, Boolean standalone = true) {
        super([name: name, type: 'react'])
        this.standalone = standalone
    }

    @Override
    protected void initializeStages() {
        validate = new SonarqubeStage(name)

        if (standalone) {
            build = new DockerBuildStage(name)
            publish = new PublishDockerStage(name, null)
            regionDeploy = new KubernetesRegionDeployStage()
            regionRollback = new KubernetesRegionRollbackStage()
        }
    }
}
