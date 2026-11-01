package cat.albirar.dynmod.filtering.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ProjectedRowTest {

    @Test
    void distinguishesNullValuesFromPathsThatWereNotRead() {
        Map<String, Object> values = new HashMap<>();
        values.put("name", "x");
        values.put("line", null);
        ProjectedRow row = new ProjectedRow(values);

        assertEquals("x", row.get("name"));
        assertTrue(row.has("line"));
        assertNull(row.get("line"));
        assertFalse(row.has("price"));
        assertThrows(IllegalArgumentException.class, () -> row.get("price"));
    }

    @Test
    void collectionsArePresentOnlyWhenRead() {
        ProjectedRow row = new ProjectedRow(Map.of());
        assertFalse(row.hasCollection("parts"));
        assertThrows(IllegalArgumentException.class, () -> row.collection("parts"));

        row.initCollection("parts");
        assertTrue(row.hasCollection("parts"));
        assertTrue(row.collection("parts").isEmpty());

        row.addElement("parts", new ProjectedRow(Map.of("number", "1")));
        assertEquals("1", row.collection("parts").get(0).get("number"));
        assertThrows(UnsupportedOperationException.class, () -> row.collection("parts").clear());
    }
}
