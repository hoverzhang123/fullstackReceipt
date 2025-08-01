package pipelines.module.stage

import org.springframework.util.CollectionUtils;
import pipelines.module.AbstractStage;
import pipelines.module.ModuleConfig;

class ClosureStage extends AbstractStage {
    List<String> envs

    ClosureStage(Closure<ModuleConfig> closure, String dockerImage, List<String> envs) {
        this.runner = closure
        this.dockerImage = dockerImage
        this.envs = envs
    }


    Boolean shouldExecuteClosure(ModuleConfig config) {
        // No Closure defined
        if(runner == null) {
            return false
        }

        // environment check
        return config.env == null || envs == null || CollectionUtils.isEmpty(envs) || envs.contains(config.env)
    }

    @Override
    protected void initializeStage() {
        shouldRun = this.&shouldExecuteClosure
    }
}
