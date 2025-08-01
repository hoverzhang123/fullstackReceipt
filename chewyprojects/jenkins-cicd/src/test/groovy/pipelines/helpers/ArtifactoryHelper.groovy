package pipelines.helpers


class ArtifactoryHelper {
    static def helmInit() {}
    static def helmPackageWithYacli(def packageRootDirectory, def appVersion, def chartVersion) {}
    static def helmPackage(def packageRootDirectory, def appVersion, def chartVersion) {}
    static def pull(String platform, String artifactName, String versionNumber, boolean explode=false) {}
    def publish(String artifacts, String version='unspecified', String prefix='unspecified') {}
}
