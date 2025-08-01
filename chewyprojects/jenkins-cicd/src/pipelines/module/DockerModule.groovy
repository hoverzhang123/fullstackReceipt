package pipelines.module

import groovy.transform.builder.Builder
import pipelines.module.stage.DockerBuildStage
import pipelines.PipelineConfig
import pipelines.module.stage.PublishDockerStage
import pipelines.module.stage.SonarqubeStage

class DockerModule extends Module {

    @Builder
    DockerModule(String name, String moduleName, String ecrRepo) {
        super([
            name: name,
            moduleName: moduleName,
            ecrRepo: ecrRepo,
        ])
    }

    DockerModule(PipelineConfig config, props) {
        super(props)
    }

    @Override
    protected void initializeStages() {
        build = new DockerBuildStage(name)
        validate = new SonarqubeStage(name)
        publish = new PublishDockerStage(name, props.getEcrRepo())
    }
}
