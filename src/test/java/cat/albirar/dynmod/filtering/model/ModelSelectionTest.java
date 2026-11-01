package cat.albirar.dynmod.filtering.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ModelSelectionTest {

    @Test
    void codesAreUniqueAndSingleCharacter() {
        Set<Character> codes = new HashSet<>();
        for (ModelPart part : ModelPart.values()) {
            assertTrue(codes.add(part.getCode()), "Duplicated code " + part.getCode());
        }
    }

    @Test
    void parsesConcatenatedCodes() {
        assertEquals(EnumSet.of(ModelPart.BRAND, ModelPart.PARTS), ModelSelection.parse("BP").parts());
    }

    @Test
    void deduplicatesRepeatedCodes() {
        assertEquals(ModelSelection.parse("BP"), ModelSelection.parse("BPPB"));
    }

    @Test
    void emptyValueSelectsNoSection() {
        assertTrue(ModelSelection.parse("").parts().isEmpty());
    }

    @Test
    void codesAreCaseSensitive() {
        InvalidModelSelectionException e = assertThrows(InvalidModelSelectionException.class,
                () -> ModelSelection.parse("Bp"));
        assertEquals("Unknown model section code 'p' in 'Bp'", e.getMessage());
    }

    @Test
    void rejectsUnknownCodesAndSeparators() {
        assertThrows(InvalidModelSelectionException.class, () -> ModelSelection.parse("X"));
        assertThrows(InvalidModelSelectionException.class, () -> ModelSelection.parse("B,P"));
        assertThrows(InvalidModelSelectionException.class, () -> ModelSelection.parse("B P"));
    }

    @Test
    void includesAnyUsesOrSemantics() {
        ModelSelection selection = ModelSelection.parse("M");
        assertTrue(selection.includesAny(new ModelPart[] { ModelPart.BRAND, ModelPart.MARKET }));
        assertFalse(selection.includesAny(new ModelPart[] { ModelPart.BRAND, ModelPart.PARTS }));
        assertFalse(selection.includesAny(new ModelPart[0]));
    }

    @Test
    void fullSelectionIncludesEverySection() {
        ModelSelection full = ModelSelection.full();
        assertTrue(full.isFull());
        assertEquals(EnumSet.allOf(ModelPart.class), full.parts());
        assertTrue(full.includes(ModelPart.ECONOMIC));
        assertTrue(full.includesAll(ModelPart.MARKET, ModelPart.ECONOMIC));
        assertFalse(ModelSelection.parse("BMPE").isFull());
        assertFalse(ModelSelection.parse("").isFull());
    }

    @Test
    void fullSelectionIsNotEqualToEveryCode() {
        // Unmarked properties are written only by the full selection
        assertNotEquals(ModelSelection.full(), ModelSelection.parse("BMPE"));
        assertEquals(ModelSelection.full(), ModelSelection.full());
    }

    @Test
    void includesChecksSingleSection() {
        ModelSelection selection = ModelSelection.parse("BP");
        assertTrue(selection.includes(ModelPart.BRAND));
        assertFalse(selection.includes(ModelPart.MARKET));
    }

    @Test
    void includesAllAppliesCascade() {
        ModelSelection selection = ModelSelection.parse("PE");
        assertTrue(selection.includesAll(ModelPart.PARTS, ModelPart.ECONOMIC));
        assertFalse(selection.includesAll(ModelPart.MARKET, ModelPart.ECONOMIC));
        assertTrue(selection.includesAll());
    }

    @Test
    void partsAreImmutable() {
        Set<ModelPart> parts = ModelSelection.parse("B").parts();
        assertThrows(UnsupportedOperationException.class, () -> parts.add(ModelPart.MARKET));
    }
}
