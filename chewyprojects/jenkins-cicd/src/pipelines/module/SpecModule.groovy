package pipelines.module

import pipelines.PipelineConfig
import pipelines.module.stage.BuildEnvironmentStage

class SpecModule extends Module {
    SpecModule(String name) {
        super([name: name,
            type: 'spec',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [task: "build"],
                    publish: [task: "artifactoryPublish -x check"],
                ],
            ],
        ])
    }

    SpecModule(PipelineConfig config, Object props) {
        super([
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [task: "build"],
                    publish: [task: "artifactoryPublish -x check"],
                ],
            ],
        ] + props)
    }

    @Override
    protected void initializeStages() {
        build = new BuildEnvironmentStage('build')
        publish = new BuildEnvironmentStage('publish')
    }
}
