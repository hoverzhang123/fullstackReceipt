package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.metadata.HelmChartUtil
import pipelines.metadata.CommonUtil

/**
 * Stage responsible for handling Helm release deployments in the pipeline.
 */
class HelmRollbackStage extends AbstractStage {

    /**
     * Rollback the helm chart to a previous version. Migrated from HelmChartModule.
     * @param config The module configuration object.
     */
    void helmRollback(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def projectName = config.projectName
        def namespace = config.namespace
        def rollbackVersion = config.previousVersion ?: CommonUtil.getRollbackVersion(jenkins, projectName, namespace, env)
        def region = config.region

        // Build cluster name using the shared utility method
        def clusterName = HelmChartUtil.buildClusterName(config, this.moduleProps)

        jenkins.env.DEPLOY_STEP = "Helm Release / Rollback - ${env} ${region}"

        if (config.isBranchDeploy) {
            namespace = jenkins.k8s.generateNamespaceName()
            jenkins.echo("HelmRollbackStage: generated branch namespace = ${namespace}")
        }

        jenkins.echo("HelmRollbackStage: namespace is ${namespace} in environment ${config.env} and region ${config.region}")

        jenkins.sh "${jenkins.ecrLogin()}"
        def revisionNumber = null
        if (rollbackVersion) {
            jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${clusterName} --alias ${clusterName}"
            def rollbackId = rollbackVersion.split('-')[0]
            // Avoid Groovy parser issues by using double quotes and escaping the awk single quotes
            def getRollbackRevisionNumber = """
            helm history ${projectName} -n ${namespace} | grep ${rollbackId} | tail -1 | awk '{print \$1}'
            """
            revisionNumber = jenkins.sh(returnStdout: true, script: getRollbackRevisionNumber).trim()
            jenkins.echo("rollback revision number = ${revisionNumber}")
        }
        jenkins.sh """yacli helm rollback \
                    --release-name ${projectName} \
                    --cluster-name ${clusterName} \
                    --force-namespace ${namespace} \
                    --helm-timeout 5m0s \
                    ${revisionNumber ?: ''}
                """
        if (env.equals("prd")) {
            jenkins.notify(projectName,
                    "Rollback succeeded, (${env}) rolled back to version ${rollbackVersion} for ${region} in ${clusterName}",
                    "blue")
        } else {
            jenkins.echo(
                    "Rollback succeeded, (${env}) rolled back to version ${rollbackVersion} for ${region} in ${clusterName}")
        }
    }

    /**
     * Initializes the stage by setting the runner method.
     */
    @Override
    void initializeStage() {
        runner = this.&helmRollback
    }
}
