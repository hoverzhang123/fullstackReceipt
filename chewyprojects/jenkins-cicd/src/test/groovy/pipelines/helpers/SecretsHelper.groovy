package pipelines.helpers


class SecretsHelper {
    static String getKVSecret(String string) {
        if (string.equals("dynatrace-terraform/cicdsrgexec/nonprod-us-app-cicd-oath")) {
            return '{"client_id": "mock-client-id", "client_secret": "mock-dynatrace-oauth-secret"}'
        }
        return string
    }
}
