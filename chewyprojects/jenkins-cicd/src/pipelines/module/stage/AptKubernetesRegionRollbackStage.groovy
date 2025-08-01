package pipelines.module.stage

import pipelines.PipelineConfig
import pipelines.metadata.CommonUtil
import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps

class AptKubernetesRegionRollbackStage extends AbstractStage {
    String namespace
    String helmTimeOut

    AptKubernetesRegionRollbackStage(String namespace, String helmTimeOut) {
        this.namespace = namespace ?: PipelineConfig.PET_HEALTH_NAMESPACE
        this.helmTimeOut = helmTimeOut
        this.dockerImage = ModuleProps.YACLI_IMAGE
    }

    void rollback(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def projectName = config.projectName
        def rollbackVersion = config.previousVersion ?: CommonUtil.getRollbackVersion(jenkins, projectName, namespace, env)

        def clusterName = 'apt-shared'
        def region = config.region

        jenkins.env.DEPLOY_STEP = "Rollback APT deployment - ${env} ${region}"
        def cluster = "${env}-${jenkins.REGIONS[region]}-${clusterName}"

        // retrieve rollback version as the last successful revision
        jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${cluster} --alias ${cluster}"
        rollbackVersion = rollbackVersion.split('-')[0]
        def getRollbackRevisionNumber = """
            helm history ${projectName} -n ${namespace} | grep ${rollbackVersion} | tail -1 | awk '{print \$1}'
            """
        def revisionNumber = jenkins.sh(returnStdout: true, script: getRollbackRevisionNumber).trim()
        jenkins.echo("rollback revision number = ${revisionNumber}")
        jenkins.sh """yacli helm rollback \\
                     --release-name ${projectName} \\
                     --cluster-name ${cluster} \\
                     --force-namespace ${namespace} \\
                     --helm-timeout ${helmTimeOut} \\
                     ${revisionNumber ?: ''}
                """
        if (env.equals("prd")) {
            jenkins.notify(projectName, "Rollback succeeded, (${env}) rolled back to version ${rollbackVersion} for ${region} in apt-shared", "blue")
        } else {
            jenkins.echo("Rollback succeeded, (${env}) rolled back to version ${rollbackVersion ?: "unknown"} for ${region} in apt-shared cluster")
        }
    }

    @Override
    void initializeStage() {
        runner = this.&rollback
    }
}
