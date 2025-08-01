package pipelines.module

import pipelines.metadata.HelmChartUtil

/**
 * Implement initializeStage which will avoid
 *   constructor issues with `this` references.
 *
 * @Override
 * void initializeStage() {
 *     this.runner = this.&stageRunner
 *     // Optional
 *     this.shouldRun = this.&stageShouldRun
 * }
 */

abstract class AbstractStage {
    protected Boolean needsInitialization = true
    protected Closure<?> runner
    protected String dockerImage
    protected Closure<Boolean> shouldRun
    protected ModuleProps moduleProps

    void setModuleProps(ModuleProps props) {
        this.moduleProps = props
        if (props.dockerImage && !this.dockerImage) {
            this.dockerImage = props.getDockerImage()
        }
    }

    void addStage(LinkedHashMap<Object, Object> stages, ModuleConfig config,
            String stage, String moduleName) {
        checkInitialization()
        def jenkins = config.jenkins

        if (shouldRun(config)) {
            def stageName = "${stage} ${moduleName ?: config.projectName}"
            stages[stageName] = {
                jenkins.stage(stageName) {
                    callRunner(jenkins, config)
                }
            }
        }
    }

    void callRunner(jenkins, ModuleConfig config) {
        if (dockerImage != null && dockerImage != "") {
            def accountId = HelmChartUtil.resolveAccountId(config, moduleProps) ?: dockerImage.split("\\.")[0]
            jenkins.sh "${jenkins.ecrLogin()}"
            jenkins.withAWS(role: "arn:aws:iam::${accountId}:role/CHEWY-cross-jenkins") {
                jenkins.docker.image(dockerImage)
                        .inside("-e HOME=${jenkins.env.WORKSPACE} --entrypoint='' -v /var/run/docker.sock:/var/run/docker.sock --group-add 992") {
                            runner.call(config)
                        }
            }
        } else {
            runner.call(config)
        }
    }

    protected abstract void initializeStage()

    protected void checkInitialization() {
        if (needsInitialization) {
            initializeStage()
            needsInitialization = false
        }
    }

    protected Boolean shouldRun(ModuleConfig config) {
        if (shouldRun != null) {
            return shouldRun.call(config)
        }
        return runner != null
    }
}
