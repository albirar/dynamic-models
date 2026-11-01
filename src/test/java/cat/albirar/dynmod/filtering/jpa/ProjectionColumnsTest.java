package cat.albirar.dynmod.filtering.jpa;

import static cat.albirar.dynmod.filtering.model.ModelPart.BRAND;
import static cat.albirar.dynmod.filtering.model.ModelPart.ECONOMIC;
import static cat.albirar.dynmod.filtering.model.ModelPart.MARKET;
import static cat.albirar.dynmod.filtering.model.ModelPart.PARTS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import cat.albirar.dynmod.filtering.model.ModelSelection;

class ProjectionColumnsTest {

    private final ProjectionColumns columns = new ProjectionColumns()
            .basic("id", "name")
            .section(BRAND, "brand.name")
            .section(ECONOMIC, "price", "margin");

    @Test
    void basicPathsAreAlwaysSelected() {
        assertEquals(List.of("id", "name"), columns.select(ModelSelection.parse("")));
        assertEquals(List.of("id", "name"), columns.select(ModelSelection.parse("MP")));
    }

    @Test
    void sectionPathsFollowTheSelection() {
        assertEquals(List.of("id", "name", "brand.name"), columns.select(ModelSelection.parse("B")));
        assertEquals(List.of("id", "name", "brand.name", "price", "margin"), columns.select(ModelSelection.full()));
    }

    @Test
    void nestedSectionsRequireTheirAncestors() {
        assertEquals(List.of("id", "name", "price", "margin"), columns.select(ModelSelection.parse("ME"), MARKET));
        assertEquals(List.of("id", "name"), columns.select(ModelSelection.parse("PE"), MARKET));
        assertEquals(List.of("id", "name"), columns.select(ModelSelection.parse("E"), PARTS));
    }

    @Test
    void sectionsNestedInSectionsRequireAllOfThem() {
        ProjectionColumns nested = new ProjectionColumns().sections(List.of(BRAND, ECONOMIC), "brand.price");
        assertEquals(List.of(), nested.select(ModelSelection.parse("B")));
        assertEquals(List.of(), nested.select(ModelSelection.parse("E")));
        assertEquals(List.of("brand.price"), nested.select(ModelSelection.parse("BE")));
        assertEquals(List.of("brand.price"), new ProjectionColumns().sections(List.of(), "brand.price")
                .select(ModelSelection.parse("")));
    }

    @Test
    void fullOnlyPathsRequireTheFullSelection() {
        ProjectionColumns unmarked = new ProjectionColumns().basic("id").fullOnly("notes");
        assertEquals(List.of("id"), unmarked.select(ModelSelection.parse("BMPE")));
        assertEquals(List.of("id", "notes"), unmarked.select(ModelSelection.full()));
    }

    @Test
    void descriptionIsCanonical() {
        ProjectionColumns first = new ProjectionColumns().basic("b", "a").section(MARKET, "m").section(BRAND, "x");
        ProjectionColumns second = new ProjectionColumns().section(BRAND, "x").basic("a").section(MARKET, "m").basic("b");
        assertEquals(first.toString(), second.toString());
        assertEquals("basic=[a, b], [BRAND]=[x], [MARKET]=[m]", first.toString());
    }

    @Test
    void repeatedPathsAreSelectedOnce() {
        ProjectionColumns repeated = new ProjectionColumns().basic("id").section(BRAND, "id", "name");
        assertEquals(List.of("id", "name"), repeated.select(ModelSelection.full()));
    }
}
