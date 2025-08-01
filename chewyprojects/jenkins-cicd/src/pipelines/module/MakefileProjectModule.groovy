/** The makefile project module is designed to build an application by targetting a few well
 *  defined targets within a specified Makefile. This modules takes a path to a Makefile which must contain
 *  the following targets:
 *     [THIS IS ALSO THE ORDER IN WHICH THEY ARE RUN]
 *     - build:
 *           Description: Builds/compiles the applications. This could build a docker file or may just compile
 *                        the application.
 *           Parameters: VERSION - The version of the project being built.
 *           Example:
 *               build:: VERSION
 *               build::
 *                   echo "Building application version ${VERSION}"
 *     - validate:
 *           Description: Unit tests the application and runs any other form of desired validation. (i.e
 *                      security scans, integration tests, sonarqube etc)
 *           Parameters: VERSION - The version of the project that is being validated.
 *           Example:
 *               validate:: VERSION
 *               validate::
 *                   echo "Validating application version ${VERSION}"
 *     - publish:
 *           Description: Wiz scan if applicable, build, and push the artifacts.
 *           Parameters: VERSION - The version of the project that is being published.
 *           Example:
 *               publish:: VERSION
 *               publish::
 *                   echo "Publishing application version ${VERSION}"
 */

package pipelines.module

import groovy.transform.builder.Builder
import pipelines.PipelineConfig

class MakefileProjectModule extends Module {
    // The path to the makefile to be used.
    protected File makefileFile

    /**
     * CONSTRUCTOR
     * @param name The name of the module/name of the project.
     * @param makefilePath The path to the makefile to be used. Ex:. './nova.mk'
     */
    @Builder
    MakefileProjectModule(String name, String makefilePath) {
        super([name: name])
        makefilePath = (makefilePath ?: 'Makefile').replace('\\', '/').trim()
        this.makefileFile = new File(makefilePath)
        if (!this.makefileFile.name) {
            throw new IllegalArgumentException('The makefilePath of the MakefileProjectModule cannot be an empty string.')
        }
    }

    MakefileProjectModule(PipelineConfig config, props) {
        super(props)
        def makefilePath = (props.makefilePath ?: 'Makefile').replace('\\', '/').trim()
        this.makefileFile = new File(makefilePath)
        if (!this.makefileFile.name) {
            throw new IllegalArgumentException('The makefilePath of the MakefileProjectModule cannot be an empty string.')
        }
    }

    /**
     * Initializes the stages of the module. All non-specialized stages default to `EmptyStage`s.
     */
    @Override
    protected void initializeStages() {
        build = Stage.builder().runner(this.&makefileBuild).build()
        validate = Stage.builder().runner(this.&makefileValidate).build()
        publish = Stage.builder().runner(this.&makefilePublish).build()
    }

    /**
     * Used by the "build" stage to run the makefile "build" target.
     * @param config The module configuration object.
     */
    protected void makefileBuild(ModuleConfig config) {
        this.runMakefileTarget(config, "build")
    }

    /**
     * Used by the "build" stage to run the makefile "validate" target.
     * @param config The module configuration object.
     */
    protected void makefileValidate(ModuleConfig config) {
        this.runMakefileTarget(config, "validate")
    }

    /**
     * Used by the "build" stage to run the makefile "publish" target.
     * @param config The module configuration object.
     */
    protected void makefilePublish(ModuleConfig config) {
        this.runMakefileTarget(config, "publish")
    }

    /**
     * Runs the specified target of the makefile.
     * @param config The module configuration object.
     * @param target The target to run.
     */
    private void runMakefileTarget(ModuleConfig config, String target) {
        config.jenkins.env.DEPLOY_STEP = "Makefile Project - ${target} - ${config.projectVersion}"
        def makeCmd = this.makefileFile.parentFile ? "make -C ${this.makefileFile.parentFile}" : "make"
        makeCmd += " -f ${this.makefileFile.name} ${target} VERSION=\"${config.projectVersion}\""
        config.jenkins.sh ([
            "#!/bin/bash",
            "set -e",
            makeCmd,
        ].join("\n"))
    }
}
