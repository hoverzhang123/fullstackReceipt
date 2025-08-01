package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps

class KubernetesRegionPostDeployStage extends AbstractStage {
    String clusterName

    KubernetesRegionPostDeployStage(String clusterName) {
        super()
        this.clusterName = clusterName
        this.dockerImage = ModuleProps.YACLI_IMAGE
    }

    void uninstall(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def projectName = config.projectName
        def namespace = config.namespace
        def region = config.region

        if (config.isBranchDeploy) {
            namespace = jenkins.k8s.generateNamespaceName()
        }

        def cluster = "${env}-${jenkins.REGIONS[region]}-${clusterName}"
        jenkins.echo("Uninstall helm chart: ${cluster}")

        jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${cluster} --alias ${cluster}"
        jenkins.sh "helm uninstall ${projectName} -n ${namespace} --kube-context ${cluster} --wait"
    }

    @Override
    void initializeStage() {
        shouldRun = { config ->
            config.uninstall
        }
        runner = this.&uninstall
    }
}
