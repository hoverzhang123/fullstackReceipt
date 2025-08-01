package pipelines.module

import pipelines.module.stage.KubernetesRegionDeployStage
import pipelines.module.stage.KubernetesRegionPostDeployStage
import pipelines.module.stage.KubernetesRegionRollbackStage

class HelmModule extends Module {
    protected String serviceModuleName

    HelmModule(String serviceModuleName) {
        super([name: 'helm'])
        this.serviceModuleName = serviceModuleName
    }

    static void pullChart(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def localFilename = "artifacts/${projectName}/${projectName}-${projectVersion}.tgz"
        if (!jenkins.fileExists(localFilename)) {
            jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS['shd']}:role/CHEWY-cross-jenkins") {
                int maxRetries = 3
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        jenkins.s3Download(
                                bucket: "${jenkins.S3_ARTIFACTS_BUCKET}",
                                file: localFilename,
                                path: "jenkins-nonprod/${projectName}-helm-charts/${projectName}-${projectVersion}.tgz")
                        break // exit the loop if the download was successful
                    } catch (Exception e) {
                        if (i == maxRetries - 1) {
                            throw e // rethrow the exception if this was the last retry
                        }
                        jenkins.sleep(1) // wait for a second before the next retry
                    }
                }
            }
        }
    }

    protected static Boolean isNotBranchDeploy(ModuleConfig config) {
        !config.isBranchDeploy
    }

    @Override
    protected void initializeStages() {
        build = Stage.builder().runner(this.&buildChart).build()
        publish = Stage.builder().runner(this.&publishChart).shouldRun(this.&isNotBranchDeploy).build()
        globalDeploy = Stage.builder().runner(this.&pullChart).shouldRun(this.&isNotBranchDeploy).build()
        regionDeploy = new KubernetesRegionDeployStage()
        regionPostDeploy = new KubernetesRegionPostDeployStage('rxp-eks-cluster')
        regionRollback = new KubernetesRegionRollbackStage()
    }

    protected void buildChart(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        jenkins.env.DEPLOY_STEP = "Build ${serviceModuleName} Chart - ${projectVersion}"

        jenkins.sh "${jenkins.ecrLogin()}"
        String helmPackageCommand = "helm package ./${serviceModuleName}/helm/${projectName} --destination artifacts/${projectName} --app-version ${projectVersion} --version ${projectVersion}"
        jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS['shd']}:role/CHEWY-cross-jenkins") {
            jenkins.docker.image(ModuleProps.CHEWY_TERRAFORM_IMAGE)
                    .inside("-e HOME=${jenkins.env.WORKSPACE} --entrypoint=''") {
                        jenkins.sh helmPackageCommand
                    }
        }
    }

    protected void publishChart(ModuleConfig config) {
        def jenkins = config.jenkins
        def projectName = config.projectName
        def projectVersion = config.projectVersion
        def previousVersion = config.previousVersion
        jenkins.env.DEPLOY_STEP = "Publish ${serviceModuleName} Chart"

        def chartLocalFilename = "artifacts/${projectName}/${projectName}-${projectVersion}.tgz"
        def chartFilename = "${projectName}-${projectVersion}.tgz"
        if (previousVersion) {
            buildChart(config)
        }

        jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS['shd']}:role/CHEWY-cross-jenkins") {
            jenkins.s3Upload(
                    bucket: "${jenkins.S3_ARTIFACTS_BUCKET}",
                    file: chartLocalFilename,
                    path: "jenkins-nonprod/${projectName}-helm-charts/${chartFilename}")
        }
    }
}
