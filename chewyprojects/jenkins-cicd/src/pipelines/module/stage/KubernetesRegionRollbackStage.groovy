package pipelines.module.stage

import pipelines.module.AbstractStage
import pipelines.module.ModuleConfig
import pipelines.module.HelmModule
import pipelines.module.ModuleProps

class KubernetesRegionRollbackStage extends AbstractStage {

    KubernetesRegionRollbackStage() {
        super()
        this.dockerImage = ModuleProps.CHEWY_TERRAFORM_IMAGE
    }

    static void rollback(ModuleConfig config) {
        def jenkins = config.jenkins
        def env = config.env
        def projectName = config.projectName
        def rollbackVersion = config.previousVersion
        def clusterName = 'rxp-eks-cluster'
        def namespace = config.namespace
        def region = config.region
        def accountId = jenkins.ACCOUNTS[env]

        jenkins.env.DEPLOY_STEP = "Rollback - ${env} ${region}"

        def cluster = "${env}-${jenkins.REGIONS[region]}-${clusterName}"

        try {
            jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${cluster} --alias ${cluster} --kubeconfig ./kube-config-${region}-${cluster}"
            def revisionNumber = null
            if (rollbackVersion) {
                rollbackVersion = rollbackVersion.split('-')[0]
                def getRollbackRevisionNumber = "helm history ${projectName} -n ${namespace} --kubeconfig ./kube-config-${region}-${cluster}" +
                        "| grep ${rollbackVersion}" +
                        '| tail -1' +
                        "| awk -F't' '{print \$1}'"
                revisionNumber = jenkins.sh(returnStdout: true, script: getRollbackRevisionNumber).trim()
                jenkins.echo("rollback revision number = ${revisionNumber}")
            }
            jenkins.sh """#!/bin/bash
                        helm rollback ${projectName} ${revisionNumber ?: ''} \
                        -n ${namespace} \
                        --kubeconfig ./kube-config-${region}-${cluster} \
                        --kube-context ${cluster} \
                        --wait
                """
            if (env.equals("prd")) {
                jenkins.notify(projectName, "Rollback succeeded, (${env}) rolled back to version ${rollbackVersion} for ${region} in ${clusterName}", "blue")
            } else {
                jenkins.echo("Rollback succeeded, (${env}) rolled back to version ${rollbackVersion} for ${region} in ${clusterName}")
            }
        } catch (Exception e) {
            jenkins.echo("Error getting revision number: ${e}")
            def getDeployedVersionCommand = "helm history ${projectName} -n ${namespace} " +
                    "--kubeconfig ./kube-config-${region}-${cluster} " +
                    "| awk 'NR==1 {line=\$0} NR>1 {print line; line=\$0}' " +
                    "| awk 'END{print \$(NF-2)}'"
            jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${cluster} --alias ${cluster} --kubeconfig ./kube-config-${region}-${cluster}"
            String deployedVersion = jenkins.sh(returnStdout: true, script: getDeployedVersionCommand).trim()

            jenkins.notifyFailure(projectName, "Rollback failed for ${env}, attempting to deploy ${deployedVersion} for ${region} in ${clusterName} instead")

            try {
                def builderConfig = ModuleConfig.builder()
                        .jenkins(config.jenkins)
                        .projectName(projectName)
                        .projectVersion(deployedVersion)
                        .build()
                HelmModule.pullChart(builderConfig)
                def chartDir = "chart/${region}"
                def valuesFile = "${chartDir}/${projectName}/values/${env}.yaml"
                jenkins.sh """helm upgrade \
                            "${projectName}" \
                            "./${projectName}-${deployedVersion}.tgz" \
                            -f ${valuesFile} \
                            --set aws.accountId=${accountId} \
                            --set aws.region=${region} \
                            --set aws.cluster=${cluster} \
                            --set aws.regionShort=${jenkins.REGIONS[region]} \
                            --set isBranchDeploy=false \
                            --install \
                            --create-namespace \
                            -n ${namespace} \
                            --kubeconfig ./kube-config-${region}-${cluster} \
                            --kube-context ${cluster} \
                            --wait \
                            --timeout 10m0s
                            """
            } catch (Exception ignored) {
                jenkins.notifyFailure(projectName, "Rollback failed for ${env}", true)
            }
        }
    }

    @Override
    void initializeStage() {
        runner = this.&rollback
    }
}
