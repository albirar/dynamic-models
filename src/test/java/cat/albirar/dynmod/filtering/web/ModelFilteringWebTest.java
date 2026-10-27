package cat.albirar.dynmod.filtering.web;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import cat.albirar.dynmod.filtering.model.ModelBasic;
import cat.albirar.dynmod.filtering.model.ModelPart;
import cat.albirar.dynmod.filtering.model.ModelSections;
import tools.jackson.databind.json.JsonMapper;

/**
 * Model filtering in a real Boot context: the auto-configuration is discovered through
 * {@code AutoConfiguration.imports} and coexists with the default MVC converters.
 */
@SpringBootTest(properties = { "models.filtratge=true", "spring.datasource.url=jdbc:h2:mem:modelFilteringWebTest",
        "spring.sql.init.mode=never" })
@AutoConfigureMockMvc
class ModelFilteringWebTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ ClassAnnotatedController.class, PlainController.class, InterfaceMethodController.class,
            SuperclassController.class, InterfaceTypeController.class })
    static class TestApplication {
    }

    @JsonInclude(Include.NON_NULL)
    record Item(
            @ModelBasic String id,
            @ModelBasic String nullable,
            @ModelSections(ModelPart.BRAND) String brand,
            @ModelSections(ModelPart.PARTS) List<Child> children,
            String unmarked) {
    }

    record Child(@ModelBasic String name, @ModelSections(ModelPart.ECONOMIC) Integer price) {
    }

    static Item item() {
        return new Item("1", null, "brand", List.of(new Child("child", 10)), "unmarked");
    }

    @RestController
    @RequestMapping("/test")
    @ModelFiltering
    static class ClassAnnotatedController {

        @GetMapping("/item")
        Item item() {
            return ModelFilteringWebTest.item();
        }

        @GetMapping("/entity")
        ResponseEntity<Item> entity() {
            return ResponseEntity.ok(ModelFilteringWebTest.item());
        }

        @GetMapping("/list")
        List<Item> list() {
            return List.of(ModelFilteringWebTest.item());
        }

        @GetMapping("/array")
        Item[] array() {
            return new Item[] { ModelFilteringWebTest.item() };
        }

        @GetMapping("/map")
        Map<String, Item> map() {
            return Map.of("k", ModelFilteringWebTest.item());
        }

        @GetMapping("/page")
        Page<Item> page() {
            return new PageImpl<>(List.of(ModelFilteringWebTest.item()));
        }

        @GetMapping("/header")
        @ModelFiltering(source = ModelFilterSource.HEADER)
        Item header() {
            return ModelFilteringWebTest.item();
        }

        @GetMapping("/custom")
        @ModelFiltering(name = "sections")
        Item custom() {
            return ModelFilteringWebTest.item();
        }

        @GetMapping("/custom-header")
        @ModelFiltering(source = ModelFilterSource.HEADER, name = "X-Parts")
        ResponseEntity<Item> customHeader() {
            return ResponseEntity.ok().header(HttpHeaders.VARY, "Accept").body(ModelFilteringWebTest.item());
        }

        @GetMapping("/vary-all")
        @ModelFiltering(source = ModelFilterSource.HEADER)
        ResponseEntity<Item> varyAll() {
            return ResponseEntity.ok().header(HttpHeaders.VARY, "*").body(ModelFilteringWebTest.item());
        }

        @GetMapping("/error")
        ResponseEntity<Item> error() {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ModelFilteringWebTest.item());
        }
    }

    @RestController
    static class PlainController {

        @GetMapping("/plain/item")
        Item item() {
            return ModelFilteringWebTest.item();
        }
    }

    interface AnnotatedMethodApi {

        @ModelFiltering
        @GetMapping("/iface-method/item")
        Item item();
    }

    @RestController
    static class InterfaceMethodController implements AnnotatedMethodApi {

        @Override
        public Item item() {
            return ModelFilteringWebTest.item();
        }
    }

    @ModelFiltering
    abstract static class AnnotatedBaseController {
    }

    @RestController
    static class SuperclassController extends AnnotatedBaseController {

        @GetMapping("/superclass/item")
        Item item() {
            return ModelFilteringWebTest.item();
        }
    }

    @ModelFiltering(source = ModelFilterSource.HEADER)
    interface AnnotatedTypeApi {
    }

    @RestController
    static class InterfaceTypeController implements AnnotatedTypeApi {

        @GetMapping("/iface-type/item")
        Item item() {
            return ModelFilteringWebTest.item();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper sharedMapper;

    @Test
    void absentSelectionReturnsFullModel() throws Exception {
        mvc.perform(get("/test/item"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmarked").value("unmarked"))
                .andExpect(jsonPath("$.brand").value("brand"))
                .andExpect(jsonPath("$.children[0].price").value(10))
                .andExpect(jsonPath("$.nullable").doesNotExist());
    }

    @Test
    void emptySelectionReturnsBasicProperties() throws Exception {
        mvc.perform(get("/test/item?m="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.brand").doesNotExist())
                .andExpect(jsonPath("$.children").doesNotExist())
                .andExpect(jsonPath("$.unmarked").doesNotExist());
        mvc.perform(get("/test/item?m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").doesNotExist());
    }

    @Test
    void selectedSectionsCascade() throws Exception {
        mvc.perform(get("/test/item?m=BP"))
                .andExpect(jsonPath("$.brand").value("brand"))
                .andExpect(jsonPath("$.children[0].name").value("child"))
                .andExpect(jsonPath("$.children[0].price").doesNotExist());
        mvc.perform(get("/test/item?m=PE"))
                .andExpect(jsonPath("$.brand").doesNotExist())
                .andExpect(jsonPath("$.children[0].price").value(10));
    }

    @Test
    void invalidOrRepeatedSelectionIsBadRequest() throws Exception {
        mvc.perform(get("/test/item?m=BX")).andExpect(status().isBadRequest());
        mvc.perform(get("/test/item?m=b")).andExpect(status().isBadRequest());
        mvc.perform(get("/test/item?m=B,P")).andExpect(status().isBadRequest());
        mvc.perform(get("/test/item?m=B&m=P")).andExpect(status().isBadRequest());
        mvc.perform(get("/test/header").header("X-Model-Sections", "B", "P")).andExpect(status().isBadRequest());
    }

    @Test
    void responseShapesAreFiltered() throws Exception {
        mvc.perform(get("/test/entity?m=")).andExpect(jsonPath("$.brand").doesNotExist()).andExpect(jsonPath("$.id").value("1"));
        mvc.perform(get("/test/list?m=")).andExpect(jsonPath("$[0].brand").doesNotExist()).andExpect(jsonPath("$[0].id").value("1"));
        mvc.perform(get("/test/array?m=")).andExpect(jsonPath("$[0].brand").doesNotExist()).andExpect(jsonPath("$[0].id").value("1"));
        mvc.perform(get("/test/map?m=")).andExpect(jsonPath("$.k.brand").doesNotExist()).andExpect(jsonPath("$.k.id").value("1"));
        mvc.perform(get("/test/page?m=B"))
                .andExpect(jsonPath("$.content[0].brand").value("brand"))
                .andExpect(jsonPath("$.content[0].unmarked").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.numberOfElements").value(1));
    }

    @Test
    void headerSourceHasNoFallbackAndAddsVary() throws Exception {
        mvc.perform(get("/test/header").header("X-Model-Sections", ""))
                .andExpect(jsonPath("$.brand").doesNotExist())
                .andExpect(header().stringValues(HttpHeaders.VARY, contains("X-Model-Sections")));
        // The method annotation replaces the class one: the query parameter is ignored
        mvc.perform(get("/test/header?m=XYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("brand"))
                .andExpect(header().stringValues(HttpHeaders.VARY, contains("X-Model-Sections")));
        // A query endpoint ignores the header
        mvc.perform(get("/test/item").header("X-Model-Sections", "XYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("brand"))
                .andExpect(header().doesNotExist(HttpHeaders.VARY));
    }

    @Test
    void customNamesReplaceDefaults() throws Exception {
        mvc.perform(get("/test/custom?sections=")).andExpect(jsonPath("$.brand").doesNotExist());
        mvc.perform(get("/test/custom?m=")).andExpect(jsonPath("$.brand").value("brand"));
        mvc.perform(get("/test/custom-header").header("X-Parts", "B"))
                .andExpect(jsonPath("$.brand").value("brand"))
                .andExpect(jsonPath("$.unmarked").doesNotExist())
                .andExpect(header().stringValues(HttpHeaders.VARY, contains("Accept", "X-Parts")));
    }

    @Test
    void varyWildcardIsKept() throws Exception {
        mvc.perform(get("/test/vary-all"))
                .andExpect(header().stringValues(HttpHeaders.VARY, contains("*")));
    }

    @Test
    void errorResponsesArePreserved() throws Exception {
        mvc.perform(get("/test/error?m=X"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.unmarked").value("unmarked"));
    }

    @Test
    void endpointWithoutAnnotationIsNotFiltered() throws Exception {
        mvc.perform(get("/plain/item?m=X").header("X-Model-Sections", "X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmarked").value("unmarked"));
    }

    @Test
    void annotationIsFoundOnInterfacesAndSuperclasses() throws Exception {
        mvc.perform(get("/iface-method/item?m=")).andExpect(jsonPath("$.brand").doesNotExist());
        mvc.perform(get("/superclass/item?m=")).andExpect(jsonPath("$.brand").doesNotExist());
        mvc.perform(get("/iface-type/item").header("X-Model-Sections", ""))
                .andExpect(jsonPath("$.brand").doesNotExist());
    }

    @Test
    void sharedMapperIsNotModified() {
        String json = sharedMapper.writeValueAsString(item());
        assertTrue(json.contains("\"unmarked\""), json);
    }
}
