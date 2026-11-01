package cat.albirar.dynmod.demo.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import cat.albirar.dynmod.demo.converter.VehicleEntityToVehicleModelBeanConverter;
import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.service.VehicleService;
import cat.albirar.dynmod.filtering.jackson.ModelFilterProvider;
import cat.albirar.dynmod.filtering.jackson.ModelFilteringModule;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import tools.jackson.databind.json.JsonMapper;

/**
 * The column projection (phase 2) produces the same response as loading entities (phase 1):
 * for every selection, filter, page and sort, both serialize to the same filtered JSON.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vehicleProjectionEquivalenceTest")
class VehicleProjectionEquivalenceTest {

    private static final List<String> SELECTIONS = List.of("FULL", "", "E", "B", "M", "P", "ME", "PE", "BMP", "BMPE");

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private VehicleEntityToVehicleModelBeanConverter converter;

    @Autowired
    private JsonMapper jsonMapper;

    static Stream<Arguments> cases() {
        List<Arguments> searches = List.of(
                search("all", criteria -> { }, PageRequest.of(0, 100, Sort.by("id"))),
                search("page 2 by model desc", criteria -> { }, PageRequest.of(1, 5, Sort.by(Direction.DESC, "model").and(Sort.by("id")))),
                search("by brand country", criteria -> { }, PageRequest.of(0, 7, Sort.by("brand.country", "id"))),
                search("brand", criteria -> criteria.setBrandManufacturerWMI("TOY"), PageRequest.of(0, 10, Sort.by("id"))),
                search("country", criteria -> criteria.setCountry("US"), PageRequest.of(0, 4, Sort.by("id"))),
                search("type", criteria -> criteria.setType(VehicleTypeEntity.Suv), PageRequest.of(1, 3, Sort.by("id"))),
                search("commercial name (distinct)", criteria -> criteria.setCommercialNamePattern("a"), PageRequest.of(0, 6, Sort.by("id"))),
                search("no results", criteria -> criteria.setBrandManufacturerWMI("NONE"), PageRequest.of(0, 10, Sort.by("id"))));
        List<Arguments> cases = new ArrayList<>();
        for (String selection : SELECTIONS) {
            for (Arguments search : searches) {
                Object[] values = search.get();
                cases.add(Arguments.of(selection, values[0], values[1], values[2]));
            }
        }
        return cases.stream();
    }

    private static Arguments search(String name, Consumer<VehicleSearchCriteria> configuration, Pageable pageable) {
        VehicleSearchCriteria criteria = new VehicleSearchCriteria();
        configuration.accept(criteria);
        return Arguments.of(name, criteria, pageable);
    }

    @ParameterizedTest(name = "m={0}: {1}")
    @MethodSource("cases")
    void projectionMatchesEntityLoading(String code, String name, VehicleSearchCriteria criteria, Pageable pageable) {
        ModelSelection selection = code.equals("FULL") ? ModelSelection.full() : ModelSelection.parse(code);

        Page<VehicleModelBean> projected = vehicleService.searchVehicleModels(criteria, selection, pageable);
        Page<VehicleEntity> entities = vehicleService.searchVehicles(criteria, selection, pageable);
        List<VehicleModelBean> loaded = entities.getContent().stream()
                .map(entity -> converter.convert(entity, selection))
                .toList();

        assertEquals(entities.getTotalElements(), projected.getTotalElements());
        assertEquals(write(loaded, selection), write(projected.getContent(), selection));
        if (!name.equals("no results")) {
            assertFalse(projected.getContent().isEmpty(), "The case should return data");
        }
    }

    private String write(List<VehicleModelBean> beans, ModelSelection selection) {
        JsonMapper mapper = jsonMapper.rebuild().addModule(new ModelFilteringModule()).build();
        return mapper.writer().with(new ModelFilterProvider(selection, null)).writeValueAsString(beans);
    }
}
