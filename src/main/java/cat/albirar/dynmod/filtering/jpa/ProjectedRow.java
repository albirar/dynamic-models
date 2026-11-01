package cat.albirar.dynmod.filtering.jpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Values read for one entity by {@link ProjectionExecutor}, keyed by the paths of the
 * {@link ProjectionPlan}. Only the paths and collections of the selected sections are present,
 * so {@link #has(String)} and {@link #hasCollection(String)} tell what was read; a present path
 * may still hold {@code null}.
 */
public final class ProjectedRow {

    private final Map<String, Object> values;
    private final Map<String, List<ProjectedRow>> collections = new HashMap<>();

    ProjectedRow(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * @return {@code true} if the path was read
     */
    public boolean has(String path) {
        return values.containsKey(path);
    }

    /**
     * @return the value of a path that was read, possibly {@code null}
     * @throws IllegalArgumentException if the path was not read
     */
    @SuppressWarnings("unchecked")
    public <V> V get(String path) {
        if (!values.containsKey(path)) {
            throw new IllegalArgumentException("Path '" + path + "' was not projected");
        }
        return (V) values.get(path);
    }

    /**
     * @return {@code true} if the collection was read, even if it has no elements
     */
    public boolean hasCollection(String attribute) {
        return collections.containsKey(attribute);
    }

    /**
     * @return the elements of a collection that was read
     * @throws IllegalArgumentException if the collection was not read
     */
    public List<ProjectedRow> collection(String attribute) {
        List<ProjectedRow> elements = collections.get(attribute);
        if (elements == null) {
            throw new IllegalArgumentException("Collection '" + attribute + "' was not projected");
        }
        return Collections.unmodifiableList(elements);
    }

    void initCollection(String attribute) {
        collections.put(attribute, new ArrayList<>());
    }

    void addElement(String attribute, ProjectedRow element) {
        collections.get(attribute).add(element);
    }
}
