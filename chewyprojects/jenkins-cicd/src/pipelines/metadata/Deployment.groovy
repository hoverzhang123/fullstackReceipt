package pipelines.metadata


class Deployment {
    boolean success
    String currentVersion
    String rollbackVersion

    @Override
    String toString() {
        return "success:${success}\ncurrentVersion:${currentVersion}\nrollbackVersion:${rollbackVersion}".toString()
    }
}
