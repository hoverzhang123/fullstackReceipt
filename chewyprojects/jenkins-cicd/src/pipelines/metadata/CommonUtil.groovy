package pipelines.metadata

import pipelines.PipelineConfig
import pipelines.module.ModuleConfig
import pipelines.module.ModuleProps

class CommonUtil {
    static final CHEWY_HELM_URL_PATTERN = ~/^(?<host>https:\/\/[^\/]*)(?:\/\()(?<pathGrouping>[^\(\)]*)(?:\))(?:\(\/\|\$\)\(\.\*\))$/

    static String getProxyBasePath(ModuleConfig config) {
        def jenkins = config.jenkins
        return config.isBranchDeploy ? "/eks/ns/${jenkins.k8s.generateNamespaceName()}/${jenkins.env.APP_NAME}" : "";
    }

    static String getUrl(jenkins, String account, String region, cluster, String appName, String namespace, String environment) {
        jenkins.withAWS(role: "arn:aws:iam::${account}:role/CHEWY-cross-jenkins") {
            jenkins.docker.image(ModuleProps.YACLI_IMAGE)
                    .inside("-e HOME=${jenkins.env.WORKSPACE} --entrypoint=''") {
                        return getUrlInsideImage(jenkins, region, cluster, appName, namespace, environment)
                    }
        }
    }

    static String getUrlInsideImage(jenkins, String region, cluster, String appName, String namespace, String environment) {
        def kubeConfigFile="./${appName}-${region}-${cluster}"
        jenkins.sh "aws eks update-kubeconfig --kubeconfig $kubeConfigFile --region ${region} --name ${cluster} --alias ${cluster}"
        def basePath = jenkins.sh(returnStdout: true, script: "kubectl get ingress -l chewy.appname=${appName} --kubeconfig $kubeConfigFile --namespace ${namespace} " +
        '--template=\'{{$itemSpecRules := index (index .items 0).spec.rules 0 }}{{ range $index, $value := $itemSpecRules.http.paths }}{{- if gt $index 0 }},{{ end }}https://{{ $itemSpecRules.host }}{{ $value.path }}{{ end }}\'').trim()
        final parseAptEndpoint = { endpoint ->
            final matcher = CHEWY_HELM_URL_PATTERN.matcher(endpoint)

            if (matcher.matches()) {
                if (matcher.group("pathGrouping").startsWith("eks/")) {
                    def groupingUrl = "https://haproxy.${jenkins.petTools.getShortRegion(region)}.lgcy.${environment}.aws.chewy.cloud/${matcher.group("pathGrouping")}"
                    return [
                        groupingUrl,
                        matcher.group("pathGrouping").startsWith('eks/app/'),
                    ]
                } else {
                    return [
                        "${matcher.group("host")}/${matcher.group("pathGrouping")}",
                        false,
                    ]
                }
            }

            return [endpoint, false]
        }

        def lbs = basePath.split(',')

        if (lbs.size() > 1) {
            def isPrioritized
            for (endpoint in lbs) {
                (basePath, isPrioritized) = parseAptEndpoint(endpoint)
                if (isPrioritized) {
                    break
                }
            }
        } else {
            basePath = parseAptEndpoint(lbs[0])[0]
        }
        return basePath
    }

    // Used to access APT shared cluster
    static String getRollbackVersion(def jenkins, String projectName, String namespace, String env) {
        jenkins.echo("getRollbackVersion: projectName = ${projectName}, namespace = ${namespace}, env = ${env}")
        if (namespace == "rxp") {
            return null
        }
        def accountId = jenkins.ACCOUNTS[env == PipelineConfig.CHOOSE_ALL_SELECT_OPTION ? "dev" : env]
        def region = jenkins.env.AWS_DEFAULT_REGION
        def clusterName = 'apt-shared'
        def cluster = "${env}-${jenkins.REGIONS[region]}-${clusterName}"
        jenkins.sh "${jenkins.ecrLogin()}"
        jenkins.withAWS(role: "arn:aws:iam::${accountId}:role/CHEWY-cross-jenkins") {
            jenkins.docker.image("${jenkins.ACCOUNTS['shd']}.dkr.ecr.${region}.amazonaws.com/plat/yacli:1.0")
                    .inside("-e HOME=${jenkins.env.WORKSPACE} --entrypoint=''") {
                        jenkins.echo("Finding most recent successful Helm revision...")

                        jenkins.sh "aws eks --region ${region} update-kubeconfig --name ${cluster} --alias ${cluster}"

                        // retrieves both revision number and app version
                        def getRollbackRevisionData = """
                        helm history ${projectName} --namespace ${namespace} | grep -B 1 deployed | head -1 | awk '{print \$1 " " \$9}'
                        """.trim()
                        def output = jenkins.sh(returnStdout: true, script: getRollbackRevisionData).trim()
                        def (rollbackRevision, appVersion) = output.tokenize(' ')
                        jenkins.echo("If rollback occurs: revision ${rollbackRevision}, app version ${appVersion}")

                        return appVersion
                    }
        }
    }

    static String getImageBaseUrl(def jenkins, String ecrRepo, String vertical, String projectName, String region = 'us-east-1') {
        def ecrHost = "${jenkins.ACCOUNTS['shd']}.dkr.ecr.${region}.amazonaws.com"
        if (ecrRepo) {
            def normalizedEcrRepo = ecrRepo.startsWith('/') ? ecrRepo : "/${ecrRepo}"
            return "${ecrHost}${normalizedEcrRepo}"
        } else {
            return "${ecrHost}/${vertical}/${projectName}"
        }
    }
}
