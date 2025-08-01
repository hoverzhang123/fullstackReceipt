package pipelines.github

import static pipelines.github.Api.deleteFromGithub
import static pipelines.github.Api.fetchFromGitHub
import static pipelines.github.Api.postToGithub
import static pipelines.github.Api.get
import static pipelines.github.Api.toJson
import static org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

/**
 * Tests for the GitHub API interactions.
 */
class ApiTest {

    private static final String API_URL = 'repos/owner/repo'
    private static final String TOKEN = 'test-token'
    private static final String BASE_URL = 'https://api.github.com/'

    private MockHttpURLConnection mockConnection
    private originalURIMetaClass
    private originalURLMetaClass

    @BeforeEach
    void setUp() {
        mockConnection = new MockHttpURLConnection()
        originalURIMetaClass = URI.metaClass
        originalURLMetaClass = URL.metaClass

        // Mock URI and URL creation
        URI.metaClass.toURL = {
            ->
            return new URL("${BASE_URL}${API_URL}")
        }

        URL.metaClass.openConnection = {
            ->
            return mockConnection
        }
    }

    @AfterEach
    void tearDown() {
        URI.metaClass = originalURIMetaClass
        URL.metaClass = originalURLMetaClass
        GroovySystem.metaClassRegistry.removeMetaClass(URI)
        GroovySystem.metaClassRegistry.removeMetaClass(URL)
    }

    @Test
    void shouldSuccessfullyDeleteFromGitHub() {
        mockConnection.responseCode = 204

        boolean result = deleteFromGithub(API_URL, TOKEN)

        assert result == true
        assert mockConnection.requestMethod == 'DELETE'
        assert mockConnection.headers['Authorization'] == "Bearer ${TOKEN}"
        assert mockConnection.headers['Accept'] == 'application/vnd.github+json'
        assert mockConnection.headers['X-GitHub-Api-Version'] == '2022-11-28'
        assert mockConnection.headers['User-Agent'] == 'red-supergiant'
        assert mockConnection.connected
        assert mockConnection.disconnected
    }

    @Test
    void shouldFailToDeleteFromGitHubWithNon204Response() {
        mockConnection.responseCode = 404

        boolean result = deleteFromGithub(API_URL, TOKEN)

        assert result == false
        assert mockConnection.requestMethod == 'DELETE'
        assert mockConnection.disconnected
    }

    @Test
    void shouldSuccessfullyFetchFromGitHub() {
        mockConnection.responseCode = 200
        mockConnection.inputStreamContent = '{"name": "test-repo", "owner": {"login": "test-owner"}}'

        Object result = fetchFromGitHub(API_URL, TOKEN)

        assert result != null
        assert result.name == 'test-repo'
        assert result.owner.login == 'test-owner'
        assert mockConnection.requestMethod == 'GET'
        assert mockConnection.headers['Authorization'] == "Bearer ${TOKEN}"
        assert mockConnection.disconnected
    }

    @Test
    void shouldThrowExceptionWhenFetchFails() {
        mockConnection.responseCode = 404

        try {
            fetchFromGitHub(API_URL, TOKEN)
            fail('Expected IOException to be thrown')
        } catch (IOException e) {
            assert e.message.contains('Failed to fetch data from GitHub. Status code: 404')
        }

        assert mockConnection.disconnected
    }

    @Test
    void shouldPostStringBodyToGitHub() {
        mockConnection.responseCode = 201
        mockConnection.inputStreamContent = '{"id": 123, "body": "test comment"}'

        Object result = postToGithub(API_URL, TOKEN, 'test comment')

        assert result != null
        assert result.id == 123
        assert result.body == 'test comment'
        assert mockConnection.requestMethod == 'POST'
        assert mockConnection.headers['Content-Type'] == 'application/json'
        assert mockConnection.doOutput == true
        assert mockConnection.outputStreamContent.contains('"body":"test comment"')
        assert mockConnection.disconnected
    }

    @Test
    void shouldPostMapBodyToGitHub() {
        mockConnection.responseCode = 201
        mockConnection.inputStreamContent = '{"id": 456, "title": "test title", "body": "test body"}'

        Map body = [title: 'test title', body: 'test body']
        Object result = postToGithub(API_URL, TOKEN, body)

        assert result != null
        assert result.id == 456
        assert result.title == 'test title'
        assert result.body == 'test body'
        assert mockConnection.requestMethod == 'POST'
        assert mockConnection.headers['Content-Type'] == 'application/json'
        assert mockConnection.outputStreamContent.contains('"title":"test title"')
        assert mockConnection.outputStreamContent.contains('"body":"test body"')
        assert mockConnection.disconnected
    }

    @Test
    void shouldThrowExceptionWhenPostFails() {
        mockConnection.responseCode = 422
        mockConnection.errorStreamContent = 'Validation failed'
        mockConnection.inputStreamContent = 'Invalid request'

        try {
            postToGithub(API_URL, TOKEN, 'test comment')
            fail('Expected IOException to be thrown')
        } catch (IOException e) {
            assert e.message.contains('Failed to post to GitHub. Status code: 422')
            assert e.message.contains('Response: Invalid request')
            assert e.message.contains('Error: Validation failed')
        }

        assert mockConnection.disconnected
    }

    @Test
    void shouldCreateGetConnection() {
        String fullUrl = "${BASE_URL}${API_URL}"

        HttpURLConnection result = get(fullUrl, TOKEN)

        assert result == mockConnection
        assert mockConnection.requestMethod == 'GET'
        assert mockConnection.headers['Authorization'] == "Bearer ${TOKEN}"
        assert mockConnection.headers['Accept'] == 'application/vnd.github+json'
        assert mockConnection.headers['X-GitHub-Api-Version'] == '2022-11-28'
        assert mockConnection.headers['User-Agent'] == 'red-supergiant'
    }

    @Test
    void shouldParseJsonFromConnection() {
        mockConnection.inputStreamContent = '{"test": "value", "number": 42}'

        Object result = toJson(mockConnection)

        assert result != null
        assert result.test == 'value'
        assert result.number == 42
        assert mockConnection.disconnected
    }

    @Test
    void shouldHandleEmptyResponseStreamsGracefully() {
        mockConnection.responseCode = 422
        mockConnection.inputStreamContent = null
        mockConnection.errorStreamContent = null

        try {
            postToGithub(API_URL, TOKEN, 'test')
            fail('Expected IOException to be thrown')
        } catch (IOException e) {
            assert e.message.contains('Response: ')
            assert e.message.contains('Error: ')
        }
    }

    @Test
    void shouldHandleIOExceptionWhenReadingResponseStreams() {
        mockConnection.responseCode = 422
        mockConnection.inputStreamThrowsException = true
        mockConnection.errorStreamThrowsException = true

        try {
            postToGithub(API_URL, TOKEN, 'test')
            fail('Expected IOException to be thrown')
        } catch (IOException e) {
            assert e.message.contains('Failed to post to GitHub. Status code: 422')
            assert e.message.contains('Response: ')
            assert e.message.contains('Error: ')
        }
    }

    // Mock HttpURLConnection class for testing
    private static class MockHttpURLConnection extends HttpURLConnection {

        Map<String, String> headers = [:]
        String requestMethod
        int responseCode = 200
        boolean doOutput = false
        boolean connected = false
        boolean disconnected = false
        String inputStreamContent = ''
        String errorStreamContent = ''
        String outputStreamContent = ''
        boolean inputStreamThrowsException = false
        boolean errorStreamThrowsException = false

        MockHttpURLConnection() {
            super(new URL('http://example.com'))
        }

        @Override
        void setRequestProperty(String key, String value) {
            headers[key] = value
        }

        @Override
        void connect() throws IOException {
            connected = true
        }

        @Override
        void disconnect() {
            disconnected = true
        }

        @Override
        InputStream getInputStream() throws IOException {
            if (inputStreamThrowsException) {
                throw new IOException('Test exception')
            }
            return inputStreamContent ? new ByteArrayInputStream(inputStreamContent.bytes) : null
        }

        @Override
        InputStream getErrorStream() {
            if (errorStreamThrowsException) {
                throw new IOException('Test exception')
            }
            return errorStreamContent ? new ByteArrayInputStream(errorStreamContent.bytes) : null
        }

        @Override
        OutputStream getOutputStream() throws IOException {
            return new ByteArrayOutputStream() {

                        @Override
                        void write(byte[] b) throws IOException {
                            super.write(b)
                            outputStreamContent = new String(b, 'UTF-8')
                        }

                    }
        }

        @Override
        boolean usingProxy() { return false }

    }

}
