package cat.albirar.dynmod.demo.e2e;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for tests against the application running on a real HTTP server. Requests are sent
 * with the JDK client, so the whole stack is exercised: HTTP parsing, MVC, services, JPA,
 * database and JSON serialization.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:httpTests")
abstract class AbstractHttpTest {

    protected static final String HEADER = "X-Model-Sections";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    /**
     * Response of a request.
     */
    protected record Response(int status, java.net.http.HttpHeaders headers, String body) {

        JsonNode json() {
            return JSON.readTree(body);
        }

        List<String> header(String name) {
            return headers.allValues(name);
        }
    }

    /**
     * Sends a GET request.
     *
     * @param pathAndQuery the path, with query string if any
     * @param headers header names and values, alternated; a name can be repeated
     */
    protected Response get(String pathAndQuery, String... headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + pathAndQuery))
                .header("Accept", "application/json")
                .GET();
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        try {
            HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers(), response.body());
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
