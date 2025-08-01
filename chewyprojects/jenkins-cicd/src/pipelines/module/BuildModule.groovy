package pipelines.module

import pipelines.module.stage.BuildEnvironmentStage
import pipelines.PipelineConfig
import pipelines.module.stage.PublishDockerStage
import pipelines.module.stage.ValidateEnvironmentStage

class BuildModule extends Module {

    BuildModule(PipelineConfig config, Object props) {
        super([
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [task: 'dockerBuildImage -x check'],
                ],
            ],
        ] + props)
    }

    @Override
    protected void initializeStages() {

        // Use new strategy pattern for both build and validate
        build = new BuildEnvironmentStage('build')
        validate = new ValidateEnvironmentStage('validate')
        publish = new PublishDockerStage(name, props.ecrRepo)
    }
}
