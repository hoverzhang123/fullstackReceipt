package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps

class KubernetesRegionDeployStage extends AbstractStage {

    KubernetesRegionDeployStage() {
        super()
        this.dockerImage = ModuleProps.CHEWY_TERRAFORM_IMAGE
    }

    static void deploy(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def clusterName = 'rxp-eks-cluster'
        def namespace = config.namespace
        jenkins.echo("KubernetesRegionDeployStage: namespace = ${namespace}")
        def isBranchDeploy = config.isBranchDeploy
        def region = config.region

        jenkins.env.DEPLOY_STEP = "Deploy - ${env} ${projectVersion}"

        // if not branch deploy, then use parameterized namespace
        if (isBranchDeploy) {
            namespace = jenkins.k8s.generateNamespaceName()
        }
        def account_id = jenkins.ACCOUNTS[env]
        // TODO: allow for parameterized cluster
        def cluster = "${env}-${jenkins.REGIONS[region]}-${clusterName}"

        def chartDir = "chart/${region}"
        def valuesFile = "${chartDir}/${projectName}/values/${env}.yaml"

        jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${cluster} --alias ${cluster} --kubeconfig ./kube-config-${region}-${cluster}"
        jenkins.sh "mkdir -p ${chartDir}"
        jenkins.sh "tar -zxvf artifacts/${projectName}/${projectName}-${projectVersion}.tgz -C ${chartDir}"

        jenkins.sh """helm upgrade \
                "${projectName}" \
                "./artifacts/${projectName}/${projectName}-${projectVersion}.tgz" \
                -f ${valuesFile} \
                --set aws.accountId=${account_id} \
                --set aws.region=${region} \
                --set aws.cluster=${cluster} \
                --set aws.regionShort=${jenkins.REGIONS[region]} \
                --set isBranchDeploy=${isBranchDeploy} \
                --install \
                --create-namespace \
                -n ${namespace} \
                --kubeconfig ./kube-config-${region}-${cluster} \
                --kube-context ${cluster} \
                --wait \
                --timeout 10m0s
                """

        if (isBranchDeploy) {
            jenkins.k8s.shortLivedNamespace(account_id, region, cluster, namespace)
        }
    }

    @Override
    void initializeStage() {
        runner = this.&deploy
    }

}
