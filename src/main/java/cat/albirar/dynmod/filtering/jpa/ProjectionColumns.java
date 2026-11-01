package cat.albirar.dynmod.filtering.jpa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import cat.albirar.dynmod.filtering.model.ModelPart;
import cat.albirar.dynmod.filtering.model.ModelSelection;

/**
 * Attribute paths of an entity grouped by section: basic paths are always read, section paths
 * only when the section is selected. A path may cross many-to-one associations
 * ({@code "brand.country"}); collections are declared with
 * {@link ProjectionPlan#collection(ModelPart, String, java.util.function.Consumer)}.
 */
public class ProjectionColumns {

    private final Set<String> basic = new LinkedHashSet<>();
    // Paths read when every part of the key is selected
    private final Map<List<ModelPart>, Set<String>> sections = new LinkedHashMap<>();
    private final Set<String> fullOnly = new LinkedHashSet<>();

    ProjectionColumns() {
    }

    /**
     * Adds paths that are always read.
     */
    public ProjectionColumns basic(String... paths) {
        basic.addAll(Arrays.asList(paths));
        return this;
    }

    /**
     * Adds paths that are read only when the section is selected.
     */
    public ProjectionColumns section(ModelPart part, String... paths) {
        return sections(List.of(part), paths);
    }

    /**
     * Adds paths that are read only when every section is selected, for sections nested in
     * other sections: {@code sections(List.of(BRAND, ECONOMIC), ...)}.
     */
    public ProjectionColumns sections(List<ModelPart> parts, String... paths) {
        if (parts.isEmpty()) {
            return basic(paths);
        }
        sections.computeIfAbsent(List.copyOf(parts), key -> new LinkedHashSet<>()).addAll(Arrays.asList(paths));
        return this;
    }

    /**
     * Adds paths that are read only for the {@link ModelSelection#full() full} selection, like
     * unmarked properties of a marked model.
     */
    public ProjectionColumns fullOnly(String... paths) {
        fullOnly.addAll(Arrays.asList(paths));
        return this;
    }

    /**
     * Returns the paths to read for a selection. A section is read when it is selected together
     * with every ancestor, the same cascade applied at serialization time.
     *
     * @param selection the selection
     * @param ancestors sections that contain these columns, outermost first
     * @return basic paths followed by the paths of the selected sections, without duplicates
     */
    List<String> select(ModelSelection selection, ModelPart... ancestors) {
        Set<String> paths = new LinkedHashSet<>(basic);
        sections.forEach((parts, sectionPaths) -> {
            List<ModelPart> path = new ArrayList<>(Arrays.asList(ancestors));
            path.addAll(parts);
            if (selection.includesAll(path.toArray(ModelPart[]::new))) {
                paths.addAll(sectionPaths);
            }
        });
        if (selection.isFull()) {
            paths.addAll(fullOnly);
        }
        return Collections.unmodifiableList(new ArrayList<>(paths));
    }

    /**
     * @return every declared path, basic, of any section or full only, without duplicates
     */
    Set<String> paths() {
        Set<String> paths = new LinkedHashSet<>(basic);
        sections.values().forEach(paths::addAll);
        paths.addAll(fullOnly);
        return Collections.unmodifiableSet(paths);
    }

    /**
     * Describes the columns in a canonical order, so that two equivalent declarations print the
     * same text regardless of the order in which paths were added.
     */
    @Override
    public String toString() {
        Map<String, Set<String>> described = new TreeMap<>();
        sections.forEach((parts, paths) -> described.put(parts.toString(), new TreeSet<>(paths)));
        StringBuilder text = new StringBuilder("basic=").append(new TreeSet<>(basic));
        described.forEach((parts, paths) -> text.append(", ").append(parts).append('=').append(paths));
        if (!fullOnly.isEmpty()) {
            text.append(", full=").append(new TreeSet<>(fullOnly));
        }
        return text.toString();
    }
}
