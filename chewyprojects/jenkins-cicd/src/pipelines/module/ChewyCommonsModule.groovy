package pipelines.module

import pipelines.PipelineConfig

/**
 * A {@link Module} for projects using the
 * <a href="https://github.com/Chewy-Inc/chewy-commons/tree/main/plugins">Chewy Commons Gradle plugins</a>.
 *
 * <p>This module provides the following hooks:
 * <dl>
 *     <dt>build</dt>
 *     <dd>Compilation, unit testing, coverage enforcement, Docker image authoring</dd>
 *     <dt>validate</dt>
 *     <dd>Wiz image scanning, Terraform validation</dd>
 *     <dt>publish</dt>
 *     <dd>Artifactory publishing, Docker image and manifest publishing, Helm Chart publishing</dd>
 *     <dt>regionDeploy</dt>
 *     <dd>Helm deploy to the APT shared cluster using Chewy YACLI</dd>
 *     <dt>regionRollback</dt>
 *     <dd>Helm rollback to the APT shared cluster using Chewy YACLI</dd>
 * </dl>
 *
 * This module requires the Chewy Commons Jenkins Agent image, e.g. nova/chewy-commons-jenkins-agent:2.8.0.
 */
class ChewyCommonsModule extends Module {

    PipelineConfig pipelineConfig
    Map properties

    /**
     * {@link ModuleFactory} constructor.
     *
     * @param pipelineConfig The {@link PipelineConfig}.
     * @param properties The module properties.
     */
    ChewyCommonsModule(final PipelineConfig pipelineConfig, final Map properties) {
        super(properties)
        this.pipelineConfig = pipelineConfig
        this.properties = properties
    }

    void gradlew(final String args) {
        pipelineConfig.jenkins.sh("./gradlew ${args}")
    }

    void build(final ModuleConfig moduleConfig) {
        moduleConfig.jenkins.withSonarQubeEnv('sonarqube-nonprod') {
            if (properties.skipCheck) {
                gradlew("-Pversion=${moduleConfig.projectVersion} -x check build")
            } else {
                gradlew("-Pversion=${moduleConfig.projectVersion} build")
            }
        }
    }

    void validate(final ModuleConfig moduleConfig) {
        if (!properties.skipVerify) {
            gradlew("-Pversion=${moduleConfig.projectVersion} verify")
        }
    }

    void publish(final ModuleConfig moduleConfig) {
        gradlew("-Pversion=${moduleConfig.projectVersion} publish")
    }

    void regionDeploy(final ModuleConfig moduleConfig) {
        if (!properties.skipDeploy) {
            gradlew(""" \
                -Pversion=${moduleConfig.projectVersion} \
                -Phelm.environment=${moduleConfig.env} \
                -Phelm.region=${moduleConfig.region} \
                deploy
            """)
        }
    }

    void regionRollback(final ModuleConfig moduleConfig) {
        gradlew(""" \
            -Pversion=${moduleConfig.projectVersion} \
            -Phelm.environment=${moduleConfig.env} \
            -Phelm.region=${moduleConfig.region} \
            rollback
        """)
    }

    @Override
    protected void initializeStages() {
        build = Stage.builder().runner(this.&build).build()
        validate = Stage.builder().runner(this.&validate).build()
        publish = Stage.builder().runner(this.&publish).build()
        regionDeploy = Stage.builder().runner(this.&regionDeploy).build()
        regionRollback = Stage.builder().runner(this.&regionRollback).build()
    }
}
