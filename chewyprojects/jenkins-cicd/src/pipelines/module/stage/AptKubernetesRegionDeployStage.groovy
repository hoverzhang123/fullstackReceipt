package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.metadata.CommonUtil
import pipelines.module.ModuleProps

class AptKubernetesRegionDeployStage extends AbstractStage {
    String namespace
    String featureDeployHelmName
    String hostPath
    String helmTimeOut

    AptKubernetesRegionDeployStage(String namespace, Object featureDeployHelmName, String hostPath,
    String helmTimeOut) {
        this.namespace = namespace
        this.featureDeployHelmName = featureDeployHelmName
        this.hostPath = hostPath
        this.helmTimeOut = helmTimeOut
        this.dockerImage = ModuleProps.YACLI_IMAGE
    }

    @Override
    void initializeStage() {
        runner = this.&deploy
    }

    void deploy(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def clusterName = 'apt-shared'
        def namespace = this.namespace ?: config.namespace
        jenkins.echo("AptKubernetesRegionDeployStage: namespace = ${namespace}")
        def isBranchDeploy = config.isBranchDeploy
        def region = config.region

        jenkins.env.DEPLOY_STEP = "Deploy to APT cluster - ${env} ${projectVersion}"
        def envValueFiles = "--tarball-values-file values/${env}.yaml"
        def chartFileName = "artifacts/helm/${projectName}-${projectVersion}.tgz"

        if (isBranchDeploy) {
            namespace = jenkins.k8s.generateNamespaceName()
            if (namespace == "dev") {
                namespace = "${projectName}-${env}-${jenkins.env.BUILD_NUMBER}"
            }
        }
        if (isBranchDeploy && featureDeployHelmName) {
            envValueFiles += " --tarball-values-file values/${featureDeployHelmName}.yaml"
        }
        def cluster = "${env}-${jenkins.REGIONS[region]}-${clusterName}"

        jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${cluster} --alias ${cluster} --kubeconfig ./${projectName}-${region}-${cluster}"
        jenkins.sh """yacli helm deploy \\
                --artifactory-user ${jenkins.env.ARTIFACTORY_USER} \\
                --artifactory-password ${jenkins.env.ARTIFACTORY_PASSWORD} \\
                --set global.version=${projectVersion} \\
                --set global.clusterInferredOverrides.environment=${env} \\
                --set global.clusterInferredOverrides.region=${region} \\
                --skip-init \\
                --chart-tarball  "${chartFileName}" \\
                --chart-name ${projectName} \\
                --release-name ${projectName} \\
                ${envValueFiles} \\
                --cluster-name "${cluster}" \\
                --verbose \\
                --force-namespace ${namespace} \\
                --helm-timeout ${helmTimeOut} \\
                --group ${jenkins.env.GROUP} \\
                --release-level dev \\
                """

        if (isBranchDeploy) {
            def previewUrl = CommonUtil.getUrlInsideImage(jenkins, region, cluster, projectName, namespace, env)
            def landingPageUrl = previewUrl.concat(hostPath ?: "/")
            jenkins.echo("Preview Environment: ${landingPageUrl}")

            try {
                jenkins.cicd.updateComment('Preview Environment: ' + landingPageUrl)
            } catch (IOException e) {
                jenkins.echo("Failed to comment on PR: ${e}")
            }
        }
    }
}
