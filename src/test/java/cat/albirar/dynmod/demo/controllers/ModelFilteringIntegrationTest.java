package cat.albirar.dynmod.demo.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Filtering of the demo endpoints through the real MVC serialization.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:modelFilteringIntegrationTest")
@AutoConfigureMockMvc
class ModelFilteringIntegrationTest {

    private static final String COROLLA = "/vehicles?brandManufacturerWMI=TOY&commercialNamePattern=Corolla&size=1";

    @Autowired
    private MockMvc mvc;

    @Test
    void vehiclesWithoutSelectionReturnFullModel() throws Exception {
        mvc.perform(get(COROLLA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].model").value("Corolla"))
                .andExpect(jsonPath("$.content[0].brand.manufacturerWMI").value("TOY"))
                .andExpect(jsonPath("$.content[0].commercialization").value("1966-11-01T00:00:00.000Z"))
                .andExpect(jsonPath("$.content[0].marketData").exists())
                .andExpect(jsonPath("$.content[0].parts").exists())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void vehiclesWithEmptySelectionReturnBasicData() throws Exception {
        mvc.perform(get(COROLLA + "&m="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].model").value("Corolla"))
                .andExpect(jsonPath("$.content[0].line").value("Corolla"))
                .andExpect(jsonPath("$.content[0].brand").doesNotExist())
                .andExpect(jsonPath("$.content[0].marketData").doesNotExist())
                .andExpect(jsonPath("$.content[0].parts").doesNotExist())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void vehiclesWithSectionsCascadeToChildren() throws Exception {
        mvc.perform(get(COROLLA + "&m=BM"))
                .andExpect(jsonPath("$.content[0].brand.commercialBrandName").value("Toyota"))
                .andExpect(jsonPath("$.content[0].marketData.*.country").exists())
                .andExpect(jsonPath("$.content[0].marketData.*.marketPrice").doesNotExist())
                .andExpect(jsonPath("$.content[0].parts").doesNotExist());
        mvc.perform(get(COROLLA + "&m=ME"))
                .andExpect(jsonPath("$.content[0].marketData.*.marketPrice").exists());
    }

    @Test
    void vehiclesRejectInvalidSelection() throws Exception {
        mvc.perform(get(COROLLA + "&m=BX")).andExpect(status().isBadRequest());
        mvc.perform(get(COROLLA + "&m=B,P")).andExpect(status().isBadRequest());
    }

    @Test
    void brandsAreNotMarkedAndKeepAllProperties() throws Exception {
        mvc.perform(get("/brands?size=1&m="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].manufacturerWMI").exists())
                .andExpect(jsonPath("$.content[0].manufacturer").exists());
    }
}
