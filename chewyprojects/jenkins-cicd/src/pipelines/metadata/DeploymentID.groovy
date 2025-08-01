package pipelines.metadata


class DeploymentID {
    String app
    String version
    String environment
    String shortRegion

    @Override
    String toString() {
        return "app:${app}\nversion:${version}\nenvironment:${environment}\nshortRegion:${shortRegion}"
    }
}
