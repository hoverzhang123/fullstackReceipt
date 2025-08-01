package pipelines.module

import pipelines.PipelineConfig
import pipelines.module.stage.DockerBuildStage
import pipelines.module.stage.PublishDockerStage
import pipelines.metadata.CommonUtil

class FlywayModule extends Module {

    FlywayModule(String name, String ecrRepo = null) {
        super([name: name, type: 'flyway',ecrRepo: ecrRepo])
    }

    FlywayModule(PipelineConfig config, props) {
        super(props)
    }

    void deployFlyway(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        def vertical = config.vertical
        def env = config.env
        jenkins.env.DEPLOY_STEP = "Deploy ${name} - ${env} ${projectVersion}"

        String dockerTag = projectVersion
        if (name != '') {
            dockerTag = "${name}-${projectVersion}"
        }

        // create image name based on ecrRepo
        def image = CommonUtil.getImageBaseUrl(jenkins, props.ecrRepo, vertical, config.projectName)
        String imageURI = "${image}:${dockerTag}"

        jenkins.sh "${jenkins.ecrLogin()}"
        jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS[env]}:role/CHEWY-cross-jenkins") {
            jenkins.sh """
                docker run \
                        -e AWS_ACCESS_KEY_ID \
                        -e AWS_SECRET_ACCESS_KEY \
                        -e AWS_SESSION_TOKEN \
                        ${imageURI} \
                        ${env} \
                        "us-east-1"
            """
        }
    }

    @Override
    protected void initializeStages() {
        build = new DockerBuildStage(name)
        publish = new PublishDockerStage(name,props.ecrRepo)
        globalDeploy = Stage.builder().runner(this.&deployFlyway).build()
    }
}
