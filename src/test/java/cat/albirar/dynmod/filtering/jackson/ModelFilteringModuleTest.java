package cat.albirar.dynmod.filtering.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import cat.albirar.dynmod.demo.models.BrandBean;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.filtering.model.ModelBasic;
import cat.albirar.dynmod.filtering.model.ModelPart;
import cat.albirar.dynmod.filtering.model.ModelSections;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.FilterProvider;
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter;
import tools.jackson.databind.ser.std.SimpleFilterProvider;

class ModelFilteringModuleTest {

    private static final FilterProvider APPLICATION_FILTERS = new SimpleFilterProvider()
            .addFilter("application", SimpleBeanPropertyFilter.serializeAllExcept("hidden"));

    private static final String BASIC_ONLY =
            "{\"id\":\"1\",\"always\":\"always\",\"plain\":{\"a\":\"a\",\"b\":\"b\"},\"appFiltered\":{\"shown\":\"s\"}}";

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new ModelFilteringModule())
            .filterProvider(new ModelFilterProvider(null, APPLICATION_FILTERS))
            .build();

    @JsonInclude(Include.NON_NULL)
    record Item(
            @ModelBasic String id,
            @ModelBasic String nullable,
            @ModelSections(ModelPart.BRAND) String brand,
            @ModelSections({ ModelPart.MARKET, ModelPart.PARTS }) String either,
            @ModelSections String always,
            String unmarked,
            @ModelBasic @JsonIgnore String ignored,
            @ModelSections(ModelPart.PARTS) List<Child> children,
            @ModelSections(ModelPart.MARKET) Map<String, Child> byKey,
            @ModelBasic Plain plain,
            @ModelBasic AppFiltered appFiltered) {
    }

    record Child(@ModelBasic String name, @ModelSections(ModelPart.ECONOMIC) Integer price, String unmarked) {
    }

    record Plain(String a, String b) {
    }

    @JsonFilter("application")
    record AppFiltered(String shown, String hidden) {
    }

    record Conflict(@ModelBasic @ModelSections(ModelPart.BRAND) String value) {
    }

    @JsonFilter("own")
    record OwnFilter(@ModelBasic String value) {
    }

    private static Item item() {
        Child child = new Child("child", 10, "x");
        return new Item("1", null, "brand", "either", "always", "unmarked", "ignored",
                List.of(child), Map.of("k", child), new Plain("a", "b"), new AppFiltered("s", "h"));
    }

    private String write(Object value, String selection) {
        if (selection == null) {
            return mapper.writeValueAsString(value);
        }
        return mapper.writer()
                .with(new ModelFilterProvider(ModelSelection.parse(selection), APPLICATION_FILTERS))
                .writeValueAsString(value);
    }

    @Test
    void withoutSelectionEveryPropertyIsWritten() {
        JsonMapper plainMapper = JsonMapper.builder().filterProvider(APPLICATION_FILTERS).build();
        assertEquals(plainMapper.writeValueAsString(item()), write(item(), null));
    }

    @Test
    void emptySelectionKeepsBasicAndUnconditionalProperties() {
        assertEquals(BASIC_ONLY,
                write(item(), ""));
    }

    @Test
    void selectedSectionIsAdded() {
        assertEquals("{\"id\":\"1\",\"brand\":\"brand\",\"always\":\"always\",\"plain\":{\"a\":\"a\",\"b\":\"b\"},\"appFiltered\":{\"shown\":\"s\"}}",
                write(item(), "B"));
    }

    @Test
    void anyOfSeveralSectionsIncludesTheProperty() {
        assertTrue(write(item(), "M").contains("\"either\""));
        assertTrue(write(item(), "P").contains("\"either\""));
    }

    @Test
    void selectionCascadesToListsAndMaps() {
        assertTrue(write(item(), "P").contains("\"children\":[{\"name\":\"child\"}]"));
        assertTrue(write(item(), "PE").contains("\"children\":[{\"name\":\"child\",\"price\":10}]"));
        assertTrue(write(item(), "M").contains("\"byKey\":{\"k\":{\"name\":\"child\"}}"));
    }

    @Test
    void childCodesCannotIncludeAnExcludedParent() {
        assertFalse(write(item(), "E").contains("children"));
    }

    @Test
    void collectionsOfMarkedBeansAreFiltered() {
        assertEquals("[" + BASIC_ONLY + "]",
                write(List.of(item()), ""));
    }

    @Test
    void conflictingMarksAreRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> write(new Conflict("v"), null));
        assertTrue(e.getMessage().contains("cannot be annotated with both @ModelBasic and @ModelSections"), e.getMessage());
    }

    @Test
    void ownJsonFilterOnMarkedBeanIsRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> write(new OwnFilter("v"), null));
        assertTrue(e.getMessage().contains("its own @JsonFilter"), e.getMessage());
    }

    @Test
    void realModelKeepsOptionalAndInstantSerialization() {
        BrandBean brand = new BrandBean();
        brand.setManufacturerWMI("TOY");
        VehicleModelBean vehicle = new VehicleModelBean();
        vehicle.setBrand(brand);
        vehicle.setModel("Corolla");
        vehicle.setLine(Optional.of("Corolla"));
        vehicle.setCommercialization(Optional.of(Instant.parse("1966-11-01T00:00:00Z")));
        vehicle.setRetirement(Optional.empty());

        String json = write(vehicle, "");
        assertTrue(json.contains("\"commercialization\":\"1966-11-01T00:00:00.000Z\""), json);
        assertTrue(json.contains("\"line\":\"Corolla\""), json);
        assertFalse(json.contains("retirement") || json.contains("brand"), json);
        assertTrue(write(vehicle, "B").contains("\"brand\":{\"manufacturerWMI\":\"TOY\"}"));
    }

    @Test
    void concurrentSelectionsDoNotInterfere() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                boolean withBrand = i % 2 == 0;
                results.add(executor.submit(() -> write(item(), withBrand ? "B" : "M").contains("\"brand\"") == withBrand));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        }
        finally {
            executor.shutdown();
        }
    }
}
