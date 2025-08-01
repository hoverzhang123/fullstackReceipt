package pipelines.github

// import static pipelines.nullability.StringNullability.assertNotBlank
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

/**
 * This class provides methods to interact with the GitHub API.
 */
class Api {

    static boolean deleteFromGithub(String apiUrl, String token) {
        HttpURLConnection connection = null
        try {
            connection = (HttpURLConnection) new URI(prependBaseUrl(apiUrl)).toURL().openConnection()
            connection.requestMethod = 'DELETE'
            setHeaders(connection, token)
            connection.connect()
            return connection.responseCode == 204
        } finally {
            connection?.disconnect()
        }
    }

    static Object fetchFromGitHub(String apiUrl, String token) {
        HttpURLConnection connection = null
        try {
            connection = (HttpURLConnection) new URI(prependBaseUrl(apiUrl)).toURL().openConnection()
            setHeaders(connection, token)
            connection.requestMethod = 'GET'

            if (connection.responseCode == 200) {
                return new JsonSlurper().parse(connection.inputStream)
            }

            throw new IOException("Failed to fetch data from GitHub. Status code: ${connection.responseCode}")
        } finally {
            connection?.disconnect()
        }
    }

    static Object postToGithub(String apiUrl, String token, String body) {
        return postToGithub(apiUrl, token, [body: body])
    }

    static Object postToGithub(String apiUrl, String token, Map body) {
        HttpURLConnection connection = null
        try {
            connection = (HttpURLConnection) new URI(prependBaseUrl(apiUrl)).toURL().openConnection()
            connection.requestMethod = 'POST'
            setHeaders(connection, token)
            connection.setRequestProperty('Content-Type', 'application/json')
            connection.doOutput = true

            connection.outputStream.write(mapToJson(body).getBytes('UTF-8'))

            if (connection.responseCode == 201) {
                return new JsonSlurper().parse(connection.inputStream)
            }

            throw new IOException("Failed to post to GitHub. Status code: ${connection.responseCode}" +
            "\nResponse: ${getResponseText(connection)}" +
            "\nError: ${getErrorText(connection)}")
        } finally {
            connection?.disconnect()
        }
    }

    static HttpURLConnection get(String apiUrl, String token) {
        HttpURLConnection connection = (HttpURLConnection) new URI(apiUrl).toURL().openConnection()

        setHeaders(connection, token)

        connection.requestMethod = 'GET'

        return connection
    }

    static Object toJson(HttpURLConnection httpURLConnection) {
        try {
            return new JsonSlurper().parse(httpURLConnection.inputStream)
        } finally {
            httpURLConnection?.disconnect()
        }
    }

    private static void setHeaders(HttpURLConnection connection, String token)  {
        connection.setRequestProperty('Authorization', "Bearer ${token}")
        connection.setRequestProperty('Accept', 'application/vnd.github+json')
        connection.setRequestProperty('X-GitHub-Api-Version', '2022-11-28')
        connection.setRequestProperty('User-Agent', 'red-supergiant')
    }

    private static String prependBaseUrl(String apiUrl) {
        return "https://api.github.com/${apiUrl}"
    }

    private static String mapToJson(Map body) {
        return new JsonBuilder(body).toString()
    }

    private static String getResponseText(HttpURLConnection connection) {
        try {
            return connection.inputStream?.text ?: ''
        } catch (IOException ignored) {
            return ''
        }
    }

    private static String getErrorText(HttpURLConnection connection) {
        try {
            return connection.errorStream?.text ?: ''
        } catch (IOException ignored) {
            return ''
        }
    }
}
