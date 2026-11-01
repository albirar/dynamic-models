package cat.albirar.dynmod.filtering.jpa;

import static cat.albirar.dynmod.filtering.model.ModelPart.BRAND;
import static cat.albirar.dynmod.filtering.model.ModelPart.ECONOMIC;
import static cat.albirar.dynmod.filtering.model.ModelPart.MARKET;
import static cat.albirar.dynmod.filtering.model.ModelPart.PARTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.annotation.JsonIgnore;

import cat.albirar.dynmod.demo.DemoApplication;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleMarketDataEntity;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.filtering.model.ModelBasic;
import cat.albirar.dynmod.filtering.model.ModelSections;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import cat.albirar.dynmod.filtering.model.ModelSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;

/**
 * Plans derived from model annotations, against the metamodel of the demo entities.
 */
@SpringBootTest(classes = DemoApplication.class, properties = "spring.datasource.url=jdbc:h2:mem:modelPlanDeriverTest")
class ModelPlanDeriverTest {

    /**
     * The plan written by hand in phase 2, before it was derived from the beans.
     */
    private static final ProjectionPlan<VehicleEntity> HAND_WRITTEN = ProjectionPlan.of(VehicleEntity.class, "id")
            .basic("line", "model", "type", "inceptionYear", "constructionYear", "commercialization", "retirement")
            .section(BRAND, "brand.manufacturerWMI", "brand.commercialBrandName", "brand.manufacturer", "brand.country")
            .collection(MARKET, "marketData", market -> market
                    .basic("country", "startCommercializing", "endCommercializing", "commercialName")
                    .section(ECONOMIC, "currency", "localMarketPrice", "marketPrice", "margin"))
            .collection(PARTS, "parts", part -> part
                    .basic("partNumber", "name", "mountingReference")
                    .section(ECONOMIC, "marketPrice", "margin"));

    @Autowired
    private EntityManager entityManager;

    private Metamodel metamodel() {
        return entityManager.getMetamodel();
    }

    private <T> ProjectionPlan<T> derive(Class<?> model, Class<T> type) {
        return ProjectionPlan.fromModel(metamodel(), model, type);
    }

    private List<String> problems(Class<?> model, Class<?> type) {
        return assertThrows(InvalidProjectionPlanException.class, () -> derive(model, type)).getProblems();
    }

    @Test
    void vehicleModelPlanEqualsTheHandWrittenOne() {
        assertEquals(HAND_WRITTEN.toString(), derive(VehicleModelBean.class, VehicleEntity.class).toString());
    }

    // Unmarked nested model with its own marked model inside, renamed sources, ignored and
    // unmarked properties, and a property in two sections
    static class Brand {
        String country;
        String manufacturer;
    }

    static class Basic {
        @ModelBasic
        String model;
        @ModelBasic
        @ModelSource("constructionYear")
        int built;
        @ModelSections(BRAND)
        Optional<Brand> brand;
        @ModelSections({ MARKET, PARTS })
        @ModelSource("brand.commercialBrandName")
        String brandName;
        String line;
        @JsonIgnore
        String notMapped;
    }

    @Test
    void sectionsFollowTheSerializationRules() {
        ProjectionPlan<VehicleEntity> plan = derive(Basic.class, VehicleEntity.class);
        assertEquals("VehicleEntity(id) basic=[constructionYear, model], [BRAND]=[brand.country, brand.manufacturer], "
                + "[MARKET]=[brand.commercialBrandName], [PARTS]=[brand.commercialBrandName], full=[line]",
                plan.toString());
        assertEquals(List.of("model", "constructionYear"), plan.columns().select(ModelSelection.parse("")));
        assertEquals(List.of("model", "constructionYear", "brand.commercialBrandName"),
                plan.columns().select(ModelSelection.parse("P")));
    }

    static class PricedBrand {
        @ModelBasic
        String country;
        @ModelSections(ECONOMIC)
        String manufacturer;
    }

    static class NestedSections {
        @ModelSections(BRAND)
        PricedBrand brand;
    }

    @Test
    void sectionsOfNestedModelsCascade() {
        ProjectionPlan<VehicleEntity> plan = derive(NestedSections.class, VehicleEntity.class);
        assertEquals("VehicleEntity(id) basic=[], [BRAND, ECONOMIC]=[brand.manufacturer], [BRAND]=[brand.country]",
                plan.toString());
        assertEquals(List.of("brand.country"), plan.columns().select(ModelSelection.parse("BM")));
        assertEquals(List.of("brand.country", "brand.manufacturer"), plan.columns().select(ModelSelection.parse("BE")));
    }

    static class Market {
        @ModelBasic
        String country;
        @ModelSections(ECONOMIC)
        float margin;
    }

    static class Markets {
        @ModelSections(MARKET)
        Map<String, Market> marketData;
        @ModelSections(PARTS)
        List<Part> parts;
    }

    static class Part {
        String name;
    }

    @Test
    void mapsAndCollectionsBecomeCollections() {
        assertEquals("VehicleEntity(id) basic=[]; MARKET marketData {basic=[country], [ECONOMIC]=[margin]}; "
                + "PARTS parts {basic=[name]}", derive(Markets.class, VehicleEntity.class).toString());
    }

    static class Unknown {
        @ModelBasic
        String modl;
        @ModelSections(BRAND)
        @ModelSource("brand.contry")
        String brandCountry;
        @ModelBasic
        @ModelSource("marketData.country")
        String throughCollection;
    }

    @Test
    void unknownSourcesAreReported() {
        assertEquals(List.of(
                "property Unknown.modl: unknown attribute 'modl' of VehicleEntity (use @ModelSource if the name differs)",
                "property Unknown.brandCountry: unknown attribute 'contry' of BrandEntity (use @ModelSource if the name differs)",
                "property Unknown.throughCollection: source 'marketData.country' can only cross many-to-one, one-to-one or embedded attributes"),
                problems(Unknown.class, VehicleEntity.class));
    }

    static class BadCollections {
        @ModelBasic
        Map<String, Market> marketData;
        @ModelSections({ PARTS, MARKET })
        List<Part> parts;
    }

    @Test
    void collectionsBelongToExactlyOneSection() {
        assertEquals(List.of("property BadCollections.marketData: a collection must belong to exactly one section",
                "property BadCollections.parts: a collection must belong to exactly one section"),
                problems(BadCollections.class, VehicleEntity.class));
    }

    @SuppressWarnings("rawtypes")
    static class RawCollection {
        @ModelSections(PARTS)
        List parts;
    }

    @Test
    void elementModelMustBeKnown() {
        assertEquals(List.of("property RawCollection.parts: cannot determine the element model of the map or collection"),
                problems(RawCollection.class, VehicleEntity.class));
    }

    static class VehicleWithParts {
        @ModelSections(PARTS)
        List<Part> parts;
    }

    static class MarketWithVehicle {
        @ModelBasic
        String country;
        @ModelSections(BRAND)
        VehicleWithParts vehicle;
    }

    @Test
    void nestedCollectionsAreNotSupported() {
        assertEquals(List.of("property VehicleWithParts.parts: collections are only supported as direct properties of the root model"),
                problems(MarketWithVehicle.class, VehicleMarketDataEntity.class));
    }

    @Test
    void rootMustBeAnEntity() {
        assertEquals(List.of("the type is not an entity"), problems(Basic.class, VehicleModelBean.class));
    }

    @Test
    void executorDerivesEachPlanOnceAndValidatesIt() {
        ProjectionExecutor executor = new ProjectionExecutor(entityManager);
        ProjectionPlan<VehicleEntity> plan = executor.planFor(VehicleModelBean.class, VehicleEntity.class);
        assertSame(plan, executor.planFor(VehicleModelBean.class, VehicleEntity.class));
        assertThrows(InvalidProjectionPlanException.class, () -> executor.planFor(Unknown.class, VehicleEntity.class));
    }
}
