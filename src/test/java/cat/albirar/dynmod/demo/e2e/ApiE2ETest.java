package cat.albirar.dynmod.demo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;

/**
 * End-to-end tests of the REST API with model filtering, against the real HTTP server and the
 * seed data of {@code data.sql}. Run them alone with {@code mvn test -Dgroups=e2e}.
 */
@Tag("e2e")
class ApiE2ETest extends AbstractHttpTest {

    private static final String ALL_VEHICLES = "/vehicles?size=100&sort=id";
    private static final String COROLLA = "/vehicles?brandManufacturerWMI=TOY&commercialNamePattern=Corolla";

    private static final Set<String> VEHICLE_BASIC = Set.of("line", "model", "type", "inceptionYear", "construction",
            "commercialization", "retirement");
    private static final Set<String> MARKET_BASIC = Set.of("country", "startCommercializing", "endCommercializing",
            "commercialName");
    private static final Set<String> MARKET_ECONOMIC = Set.of("currency", "localMarketPrice", "marketPrice", "margin");
    private static final Set<String> PART_BASIC = Set.of("partNumber", "name", "mountingReference");
    private static final Set<String> PART_ECONOMIC = Set.of("marketPrice", "margin");

    private Response ok(String pathAndQuery, String... headers) {
        Response response = get(pathAndQuery, headers);
        assertThat(response.status()).as("%s %s", pathAndQuery, response.body()).isEqualTo(200);
        return response;
    }

    private static List<JsonNode> vehicles(Response response) {
        return new ArrayList<>(response.json().path("content").values());
    }

    private static List<JsonNode> children(JsonNode vehicle, String property) {
        return new ArrayList<>(vehicle.path(property).values());
    }

    private static JsonNode corolla(Response response) {
        List<JsonNode> content = vehicles(response);
        assertThat(content).hasSize(1);
        return content.get(0);
    }

    // --- Without selection the API behaves as before ---------------------------------------

    @Test
    void withoutSelectionTheFullModelIsReturned() {
        JsonNode corolla = corolla(ok(COROLLA));

        assertThat(corolla.path("model").asString()).isEqualTo("Corolla");
        assertThat(corolla.path("commercialization").asString()).isEqualTo("1966-11-01T00:00:00.000Z");
        assertThat(corolla.path("brand").path("commercialBrandName").asString()).isEqualTo("Toyota");
        assertThat(corolla.path("marketData").propertyNames()).containsExactlyInAnyOrder("US", "ES");
        assertThat(corolla.path("marketData").path("US").propertyNames()).containsAll(MARKET_ECONOMIC);
        assertThat(corolla.path("parts").propertyNames()).containsExactlyInAnyOrder("TRD-001", "TRD-002");
        assertThat(corolla.path("parts").path("TRD-001").path("marketPrice").asDouble()).isEqualTo(25.0);
    }

    @Test
    void selectingEverySectionEqualsTheFullModel() {
        String full = ok(ALL_VEHICLES).body();
        assertThat(ok(ALL_VEHICLES + "&m=BMPE").body()).isEqualTo(full);
        assertThat(ok(ALL_VEHICLES, HEADER, "EPMB").body()).isEqualTo(full);
    }

    // --- Sections ----------------------------------------------------------------------------

    @Test
    void emptySelectionReturnsOnlyBasicData() {
        Response full = ok(ALL_VEHICLES);
        Response basic = ok(ALL_VEHICLES + "&m=");

        assertThat(basic.json().path("totalElements")).isEqualTo(full.json().path("totalElements"));
        assertThat(vehicles(basic)).hasSameSizeAs(vehicles(full)).allSatisfy(vehicle -> {
            assertThat(VEHICLE_BASIC).containsAll(vehicle.propertyNames());
            assertThat(vehicle.has("model")).isTrue();
        });
        assertThat(basic.body().length()).isLessThan(full.body().length() / 2);
    }

    @Test
    void brandSectionAddsOnlyTheBrand() {
        JsonNode corolla = corolla(ok(COROLLA + "&m=B"));
        assertThat(corolla.path("brand").path("manufacturerWMI").asString()).isEqualTo("TOY");
        assertThat(corolla.has("marketData")).isFalse();
        assertThat(corolla.has("parts")).isFalse();
    }

    @Test
    void marketSectionCascadesToMarketDataWithoutEconomicData() {
        JsonNode corolla = corolla(ok(COROLLA + "&m=M"));
        assertThat(corolla.has("brand")).isFalse();
        assertThat(children(corolla, "marketData")).hasSize(2)
                .allSatisfy(market -> assertThat(MARKET_BASIC).containsAll(market.propertyNames()));

        JsonNode withPrices = corolla(ok(COROLLA + "&m=ME"));
        assertThat(children(withPrices, "marketData"))
                .allSatisfy(market -> assertThat(market.propertyNames()).containsAll(MARKET_ECONOMIC));
    }

    @Test
    void partsSectionCascadesToPartsWithoutEconomicData() {
        JsonNode corolla = corolla(ok(COROLLA + "&m=P"));
        assertThat(children(corolla, "parts")).hasSize(2)
                .allSatisfy(part -> assertThat(part.propertyNames()).containsExactlyInAnyOrderElementsOf(PART_BASIC));

        JsonNode withPrices = corolla(ok(COROLLA + "&m=PE"));
        assertThat(children(withPrices, "parts"))
                .allSatisfy(part -> assertThat(part.propertyNames()).containsAll(PART_ECONOMIC));
    }

    @Test
    void economicSectionAloneDoesNotIncludeItsParents() {
        assertThat(corolla(ok(COROLLA + "&m=E")).propertyNames()).isSubsetOf(VEHICLE_BASIC);
    }

    @Test
    void repeatedCodesAreAccepted() {
        assertThat(ok(COROLLA + "&m=BBPB").body()).isEqualTo(ok(COROLLA + "&m=BP").body());
    }

    // --- Query parameter and header ----------------------------------------------------------

    @Test
    void headerGivesTheSameResultAsQueryParameter() {
        assertThat(ok(COROLLA, HEADER, "BP").body()).isEqualTo(ok(COROLLA + "&m=BP").body());
        assertThat(ok(COROLLA, HEADER, "").body()).isEqualTo(ok(COROLLA + "&m=").body());
    }

    @Test
    void queryParameterTakesPrecedenceWhenBothArePresent() {
        String marketOnly = ok(COROLLA + "&m=M").body();
        assertThat(ok(COROLLA + "&m=M", HEADER, "B").body()).isEqualTo(marketOnly);
        assertThat(ok(COROLLA + "&m=M", HEADER, "not valid").body()).isEqualTo(marketOnly);
        assertThat(ok(COROLLA + "&m=", HEADER, "BMPE").body()).isEqualTo(ok(COROLLA + "&m=").body());
    }

    @Test
    void responsesDeclareTheHeaderInVary() {
        assertThat(ok(COROLLA).header("Vary")).contains(HEADER);
        assertThat(ok(COROLLA + "&m=B").header("Vary")).contains(HEADER);
        assertThat(ok("/brands?size=1").header("Vary")).contains(HEADER);
    }

    // --- Errors ------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "m=X", "m=BX", "m=b", "m=B,P", "m=B%20P", "m=B&m=P" })
    void invalidQueryParameterIsBadRequest(String query) {
        Response response = get(COROLLA + "&" + query, HEADER, "B");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().path("status").asInt()).isEqualTo(400);
    }

    @Test
    void invalidHeaderIsBadRequestWhenUsed() {
        assertThat(get(COROLLA, HEADER, "BX").status()).isEqualTo(400);
        assertThat(get(COROLLA, HEADER, "B", HEADER, "P").status()).isEqualTo(400);
    }

    @Test
    void endpointsWithoutAnnotationIgnoreTheSelection() {
        Response response = ok("/appVersion?m=X", HEADER, "X");
        assertThat(response.json().path("name").asString()).isEqualTo("dynamic-models");
        assertThat(response.header("Vary")).doesNotContain(HEADER);
    }

    // --- Search, pagination and other models -------------------------------------------------

    @Test
    void searchAndPaginationAreIndependentOfTheSelection() {
        String page = "/vehicles?type=Sedan&size=3&page=1&sort=id";
        Response full = ok(page);
        Response basic = ok(page + "&m=");

        assertThat(full.json().path("totalElements").asLong()).isEqualTo(9);
        assertThat(basic.json().path("totalElements")).isEqualTo(full.json().path("totalElements"));
        assertThat(vehicles(basic)).extracting(vehicle -> vehicle.path("model").asString())
                .containsExactlyElementsOf(vehicles(full).stream().map(vehicle -> vehicle.path("model").asString()).toList());
    }

    @Test
    void vehiclesMatchingSeveralMarketsAreNotDuplicated() {
        Response response = ok(COROLLA + "&size=10");
        assertThat(response.json().path("totalElements").asLong()).isEqualTo(1);
        assertThat(vehicles(response)).hasSize(1);
    }

    @Test
    void brandsHaveNoMarksAndAreAlwaysComplete() {
        String full = ok("/brands?size=100&sort=manufacturerWMI").body();
        assertThat(ok("/brands?size=100&sort=manufacturerWMI&m=").body()).isEqualTo(full);
        assertThat(get("/brands?m=X").status()).isEqualTo(400);
    }

    // --- Concurrency -------------------------------------------------------------------------

    @Test
    void concurrentRequestsWithDifferentSelectionsDoNotInterfere() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                int variant = i % 3;
                results.add(executor.submit(() -> {
                    JsonNode corolla = switch (variant) {
                        case 0 -> corolla(ok(COROLLA + "&m=B"));
                        case 1 -> corolla(ok(COROLLA, HEADER, "M"));
                        default -> corolla(ok(COROLLA));
                    };
                    return switch (variant) {
                        case 0 -> corolla.has("brand") && !corolla.has("marketData");
                        case 1 -> !corolla.has("brand") && corolla.has("marketData") && !corolla.has("parts");
                        default -> corolla.has("brand") && corolla.has("marketData") && corolla.has("parts");
                    };
                }));
            }
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        }
        finally {
            executor.shutdown();
        }
    }
}
