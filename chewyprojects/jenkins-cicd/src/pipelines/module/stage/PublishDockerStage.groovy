package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.metadata.CommonUtil

class PublishDockerStage extends AbstractStage {
    String moduleName
    String ecrRepo

    PublishDockerStage(String moduleName, String ecrRepo) {
        this.moduleName = moduleName
        this.ecrRepo = ecrRepo
    }

    static void publishImage(Object jenkins, String image, String name, String projectVersion, String previousVersion) {
        if (previousVersion) {
            if(name) {
                jenkins.sh "docker tag ${image}:${name}-${previousVersion} ${image}:${name}-${projectVersion}"
            } else {
                jenkins.sh "docker tag ${image}:${previousVersion} ${image}:${projectVersion}"
            }
        }

        if(name) {
            jenkins.sh "docker push ${image}:${name}-${projectVersion}"
        } else {
            jenkins.sh "docker push ${image}:${projectVersion}"
        }
    }

    @Override
    void initializeStage() {
        runner = this.&publish
    }

    void publish(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def previousVersion = config.previousVersion
        def stageName = moduleName ?: config.projectName
        String vertical = config.vertical
        jenkins.echo("ModuleConfig vertical: ${vertical}")
        jenkins.env.DEPLOY_STEP = "Publish ${stageName} - ${projectVersion})"

        jenkins.sh "${jenkins.ecrLogin()}"
        jenkins.echo("PublishDockerStage.groovy ecrRepo: ${ecrRepo}")
        def image = CommonUtil.getImageBaseUrl(jenkins, ecrRepo, vertical, projectName)
        if (ecrRepo) {
            publishImage(jenkins, image, null, projectVersion, null)
        } else {
            publishImage(jenkins, image, moduleName, projectVersion, previousVersion)
        }
    }
}
