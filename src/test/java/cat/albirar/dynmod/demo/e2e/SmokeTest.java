package cat.albirar.dynmod.demo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests: the application starts and its main features answer. Run them alone with
 * {@code mvn test -Dgroups=smoke}.
 */
@Tag("smoke")
class SmokeTest extends AbstractHttpTest {

    @Test
    void applicationAnswersItsVersion() {
        Response response = get("/appVersion");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().path("name").asString()).isEqualTo("dynamic-models");
    }

    @Test
    void vehiclesAreListed() {
        Response response = get("/vehicles?size=1");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("Content-Type")).anyMatch(value -> value.startsWith("application/json"));
        assertThat(response.json().path("content").size()).isEqualTo(1);
        assertThat(response.json().path("totalElements").asLong()).isPositive();
    }

    @Test
    void brandsAreListed() {
        Response response = get("/brands?size=1");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().path("content").size()).isEqualTo(1);
    }

    @Test
    void filteringIsActive() {
        Response response = get("/vehicles?size=1&m=");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().path("content").get(0).has("brand")).isFalse();
        assertThat(response.header("Vary")).contains(HEADER);
        assertThat(get("/vehicles?size=1&m=X").status()).isEqualTo(400);
    }

    @Test
    void apiDocumentationIsPublished() {
        Response response = get("/v3/api-docs");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("/vehicles", "/brands");
    }
}
