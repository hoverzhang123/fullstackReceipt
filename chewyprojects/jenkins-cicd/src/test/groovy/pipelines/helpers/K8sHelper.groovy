package pipelines.helpers


class K8sHelper {
    static String generateNamespaceName() {
        return "namespace"
    }

    static void shortLivedNamespace(String account, String region, String cluster, String namespace = '', String dockerImage = 'ecrLink') {
        println "k8s.shortLivedNamespace ${account} ${region} ${cluster} ${namespace} ${dockerImage}"
    }

    static boolean isProdEnvironment(Boolean isProd) {
        return isProd
    }

    static void uninstall(String account, String region, String cluster, String application, String namespace='', String dockerImage='ecrLink') {
        println "k8s.uninstall ${account} ${region} ${cluster} ${application} ${namespace} ${dockerImage}"
    }
}
