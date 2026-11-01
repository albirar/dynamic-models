package cat.albirar.dynmod.filtering.jpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import cat.albirar.dynmod.filtering.model.ModelPart;
import jakarta.persistence.metamodel.Metamodel;

/**
 * Declares, for one resource, which entity attributes belong to each section of its model.
 * {@link ProjectionExecutor} turns the plan and a selection into queries that read only the
 * selected columns:
 *
 * <pre>
 * ProjectionPlan.of(VehicleEntity.class, "id")
 *     .basic("model", "type")
 *     .section(BRAND, "brand.commercialBrandName")
 *     .collection(MARKET, "marketData", m -&gt; m
 *         .basic("country")
 *         .section(ECONOMIC, "marketPrice"));
 * </pre>
 *
 * The plan is configured once, usually in a constant, and must not be modified afterwards.
 * Instead of declaring it by hand, it can be derived from the annotations of the model with
 * {@link #fromModel(Metamodel, Class, Class)}.
 *
 * @param <T> the root entity
 */
public final class ProjectionPlan<T> {

    private final Class<T> type;
    private final String idPath;
    private final ProjectionColumns columns = new ProjectionColumns();
    private final List<CollectionPlan> collections = new ArrayList<>();

    private ProjectionPlan(Class<T> type, String idPath) {
        this.type = Objects.requireNonNull(type, "type");
        this.idPath = Objects.requireNonNull(idPath, "idPath");
    }

    /**
     * @param type the root entity
     * @param idPath the identifier attribute, always read and used to load collections
     */
    public static <T> ProjectionPlan<T> of(Class<T> type, String idPath) {
        return new ProjectionPlan<>(type, idPath);
    }

    /**
     * Derives the plan from the model annotations, so the sections of the projection are the
     * ones of the serialization: {@code @ModelBasic} properties are basic, {@code @ModelSections}
     * properties belong to their sections, and {@code @ModelSource} names the entity attribute
     * when it differs from the property name. Properties holding a model read from a
     * many-to-one association contribute its columns; properties holding a map or collection of
     * models read from a collection association become collections.
     *
     * @param metamodel the JPA metamodel
     * @param model the response model
     * @param type the entity the model is read from
     * @return the plan; it is not validated, see {@link ProjectionExecutor#planFor(Class, Class)}
     * @throws InvalidProjectionPlanException if the model cannot be mapped to the entity
     */
    public static <T> ProjectionPlan<T> fromModel(Metamodel metamodel, Class<?> model, Class<T> type) {
        return ModelPlanDeriver.derive(metamodel, model, type);
    }

    /**
     * Adds root paths that are always read.
     */
    public ProjectionPlan<T> basic(String... paths) {
        columns.basic(paths);
        return this;
    }

    /**
     * Adds root paths, possibly through many-to-one associations, read only when the section is
     * selected.
     */
    public ProjectionPlan<T> section(ModelPart part, String... paths) {
        columns.section(part, paths);
        return this;
    }

    /**
     * Adds a collection association read with one extra query per page, only when the section
     * is selected. Its columns are declared relative to the collection element, and its
     * sections apply only together with {@code part}.
     *
     * @param part the section of the collection
     * @param attribute the collection attribute of the root entity
     * @param configuration declares the columns of the elements
     */
    public ProjectionPlan<T> collection(ModelPart part, String attribute, Consumer<ProjectionColumns> configuration) {
        ProjectionColumns elementColumns = new ProjectionColumns();
        configuration.accept(elementColumns);
        collections.add(new CollectionPlan(part, attribute, elementColumns));
        return this;
    }

    Class<T> type() {
        return type;
    }

    String idPath() {
        return idPath;
    }

    ProjectionColumns columns() {
        return columns;
    }

    List<CollectionPlan> collections() {
        return Collections.unmodifiableList(collections);
    }

    /**
     * Describes the plan in a canonical order: two equivalent plans print the same text.
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(type.getSimpleName()).append('(').append(idPath).append(") ").append(columns);
        collections.stream()
                .sorted((first, second) -> first.attribute().compareTo(second.attribute()))
                .forEach(collection -> text.append("; ").append(collection.part()).append(' ')
                        .append(collection.attribute()).append(" {").append(collection.columns()).append('}'));
        return text.toString();
    }

    record CollectionPlan(ModelPart part, String attribute, ProjectionColumns columns) {
    }
}
