package pipelines.module

import groovy.transform.builder.Builder
import pipelines.PipelineConfig
import pipelines.module.stage.ClosureStage

class ClosureModule extends Module  {
    Map<String, Closure<ModuleConfig>> closureMap
    String dockerImage
    List<String> envs

    @Builder
    ClosureModule(
    String name,
    Map<String, Closure<ModuleConfig>> closureMap,
    String dockerImage,
    List<String> envs
    ) {
        super([name: name])
        this.closureMap = closureMap
        this.dockerImage = dockerImage
        this.envs = envs ?: []
    }

    ClosureModule(PipelineConfig config, props) {
        super(props)
        this.closureMap = props.closureMap
        this.dockerImage = props.dockerImage
        this.envs = props.envs ?: []
    }

    @Override
    protected void initializeStages() {
        build = new ClosureStage(closureMap.get('build'), dockerImage, envs)
        validate = new ClosureStage(closureMap.get('validate'), dockerImage, envs)
        publish = new ClosureStage(closureMap.get('publish'), dockerImage, envs)
        preCheck = new ClosureStage(closureMap.get('preCheck'), dockerImage, envs)
        regionDeploy = new ClosureStage(closureMap.get('regionDeploy'),dockerImage, envs)
        postCheck = new ClosureStage(closureMap.get('postCheck'), dockerImage, envs)
        regionRollback = new ClosureStage(closureMap.get('regionRollback'), dockerImage, envs)
    }
}
