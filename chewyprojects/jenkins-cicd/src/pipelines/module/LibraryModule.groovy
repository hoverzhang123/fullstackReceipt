package pipelines.module

import pipelines.module.stage.PublishLibraryStage
import pipelines.module.stage.SonarqubeStage

class LibraryModule extends Module {
    List<String> libraries

    LibraryModule(List<String> libraries = []) {
        super([name: libraries.size() > 1 ? 'libraries' : 'library'])
        this.libraries = libraries
    }

    void buildLibrary(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        for (String library : libraries) {
            jenkins.env.DEPLOY_STEP = "Build Library - ${library} - ${projectVersion}"
            jenkins.sh """#!/bin/bash
                ./gradlew -PprojectVersion=${projectVersion} ${library}:assemble -x test
            """
        }
    }

    void validateLibrary(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        for (String library: libraries) {
            jenkins.env.DEPLOY_STEP = "Build Library - ${library} - ${projectVersion}"
            SonarqubeStage.checkSonarQube(config, library)
        }
    }

    def publishLibrary(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectVersion = config.projectVersion
        for (String library: libraries) {
            jenkins.env.DEPLOY_STEP = "Publish Library - ${library} - ${projectVersion}"
            PublishLibraryStage.publishLibrary(jenkins, library, projectVersion)
        }
    }

    @Override
    protected void initializeStages() {
        build = Stage.builder().runner(this.&buildLibrary).build()
        validate = Stage.builder().runner(this.&validateLibrary).build()
        publish = Stage.builder().runner(this.&publishLibrary).build()
    }
}
