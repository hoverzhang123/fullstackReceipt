package pipelines.helpers

class PetToolsHelper {
    static String getShortRegion(String region) {
        return region == "us-east-1" ? "use1" : "use2"
    }
}
