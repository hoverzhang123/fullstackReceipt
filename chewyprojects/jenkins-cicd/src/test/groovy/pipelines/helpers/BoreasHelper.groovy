package pipelines.helpers


class BoreasHelper {
    static String createCR(Map open_data, String snow_environment) {
        return "CHG123456"
    }

    static renderCR(String cr_number, Map render_data, String snow_environment) {}

    static startCR(String cr_number, String snow_environment, String user, boolean respect_change_freezes) {}

    static closeCR(String cr_number, Object close_data) {}

    static boolean checkChangeFreezeExists(String env) {
        return false
    }

    static validateAndStartChange(String cr_number, Boolean emergency, String snow_environment, boolean respect_change_freezes) {}
}
