package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig

class PublishLibraryStage extends AbstractStage {
    String moduleName
    PublishLibraryStage(String moduleName) {
        this.moduleName =  moduleName
    }

    static void publishLibrary(Object jenkins, String name, String projectVersion) {
        jenkins.echo("PUBLISH LIBRARY TEST for ${name} with version ${projectVersion}")
        String gradleTask = ""
        if (projectVersion.contains("SNAPSHOT")) {
            gradleTask = "snapshot"
        } else if (projectVersion.contains("dev")) {
            gradleTask = "devSnapshot"
        } else {
            gradleTask = "final"
        }
        jenkins.withCredentials([
            jenkins.usernamePassword(credentialsId: 'jenkins-github-userpass', passwordVariable: 'GRGIT_PASS', usernameVariable: 'GRGIT_USER')
        ]) {
            jenkins.sh "./gradlew -PprojectVersion=${projectVersion} ${gradleTask} -x release ${name}:artifactoryPublish"
        }
    }

    @Override
    void initializeStage() {
        runner = this.&publish
    }

    void publish(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        def stageName = moduleName ?: config.projectName
        jenkins.env.DEPLOY_STEP = "Publish library - ${stageName} - ${projectVersion})"

        jenkins.sh "${jenkins.ecrLogin()}"
        publishLibrary(jenkins, moduleName, projectVersion)
    }
}
