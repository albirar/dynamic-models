package cat.albirar.dynmod.filtering.jpa;

import static cat.albirar.dynmod.filtering.model.ModelPart.BRAND;
import static cat.albirar.dynmod.filtering.model.ModelPart.ECONOMIC;
import static cat.albirar.dynmod.filtering.model.ModelPart.MARKET;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import cat.albirar.dynmod.demo.DemoApplication;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;

/**
 * Plans are checked against the metamodel of the demo entities.
 */
@SpringBootTest(classes = DemoApplication.class, properties = "spring.datasource.url=jdbc:h2:mem:projectionPlanValidatorTest")
class ProjectionPlanValidatorTest {

    @Autowired
    private EntityManager entityManager;

    private Metamodel metamodel() {
        return entityManager.getMetamodel();
    }

    private List<String> problems(ProjectionPlan<?> plan) {
        return assertThrows(InvalidProjectionPlanException.class,
                () -> ProjectionPlanValidator.validate(metamodel(), plan)).getProblems();
    }

    @Test
    void demoPlanIsValid() {
        ProjectionPlan<VehicleEntity> plan = ProjectionPlan.fromModel(metamodel(), VehicleModelBean.class, VehicleEntity.class);
        assertDoesNotThrow(() -> ProjectionPlanValidator.validate(metamodel(), plan));
    }

    @Test
    void rootTypeMustBeAnEntity() {
        assertEquals(List.of("the type is not an entity"),
                problems(ProjectionPlan.of(VehicleModelBean.class, "model")));
    }

    @Test
    void unknownAttributesAreReported() {
        assertEquals(List.of("unknown attribute 'modl' of VehicleEntity in path 'modl'",
                "unknown attribute 'contry' of BrandEntity in path 'brand.contry'"),
                problems(ProjectionPlan.of(VehicleEntity.class, "id")
                        .basic("modl")
                        .section(BRAND, "brand.contry")));
    }

    @Test
    void identifierIsChecked() {
        assertEquals(List.of("unknown attribute 'key' of VehicleEntity in path 'key'"),
                problems(ProjectionPlan.of(VehicleEntity.class, "key")));
    }

    @Test
    void pathsMustEndInBasicAttributes() {
        assertEquals(List.of("path 'brand' must end in a basic attribute, but 'brand' is many-to-one"),
                problems(ProjectionPlan.of(VehicleEntity.class, "id").section(BRAND, "brand")));
    }

    @Test
    void pathsCannotCrossCollectionsOrBasicAttributes() {
        assertEquals(List.of(
                "path 'marketData.country' can only cross many-to-one or one-to-one associations, but 'marketData' is one-to-many",
                "path 'model.length' can only cross many-to-one or one-to-one associations, but 'model' is basic"),
                problems(ProjectionPlan.of(VehicleEntity.class, "id").basic("marketData.country", "model.length")));
    }

    @Test
    void collectionsAreChecked() {
        assertEquals(List.of("unknown attribute 'markets' of VehicleEntity",
                "collection 'brand' is not a collection attribute",
                "path 'marketData.vehicle' must end in a basic attribute, but 'vehicle' is many-to-one",
                "unknown attribute 'price' of VehicleMarketDataEntity in path 'marketData.price'"),
                problems(ProjectionPlan.of(VehicleEntity.class, "id")
                        .collection(MARKET, "markets", market -> market.basic("country"))
                        .collection(MARKET, "brand", brand -> brand.basic("country"))
                        .collection(MARKET, "marketData", market -> market
                                .basic("country", "vehicle")
                                .section(ECONOMIC, "price"))));
    }

    @Test
    void executorChecksPlansBeforeQuerying() {
        ProjectionExecutor executor = new ProjectionExecutor(entityManager);
        ProjectionPlan<VehicleEntity> plan = ProjectionPlan.of(VehicleEntity.class, "id").basic("modl");
        Specification<VehicleEntity> all = (root, query, cb) -> null;

        InvalidProjectionPlanException e = assertThrows(InvalidProjectionPlanException.class,
                () -> executor.findAll(plan, all, ModelSelection.full(), PageRequest.of(0, 10)));
        assertTrue(e.getMessage().startsWith("Invalid projection plan for " + VehicleEntity.class.getName()), e.getMessage());
        // An invalid plan is not remembered as valid
        assertThrows(InvalidProjectionPlanException.class, () -> executor.validate(plan));
    }
}
