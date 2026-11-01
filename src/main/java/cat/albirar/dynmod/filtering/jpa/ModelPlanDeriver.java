package cat.albirar.dynmod.filtering.jpa;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import cat.albirar.dynmod.filtering.model.ModelBasic;
import cat.albirar.dynmod.filtering.model.ModelPart;
import cat.albirar.dynmod.filtering.model.ModelSections;
import cat.albirar.dynmod.filtering.model.ModelSource;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * Derives a {@link ProjectionPlan} from the annotations of a model, applying the same rules as
 * the serialization filter:
 * <ul>
 * <li>{@code @ModelBasic}, or {@code @ModelSections} without sections: read whenever the
 * enclosing model is read;</li>
 * <li>{@code @ModelSections}: read when any of its sections is selected, together with the
 * sections of the enclosing properties;</li>
 * <li>a property without marks in a model with marks: read only for the full selection;</li>
 * <li>every property of a model without marks: read whenever the model is read.</li>
 * </ul>
 * The entity attribute of a property ({@code @ModelSource}, or the property name) decides how it
 * is read: a basic attribute is a column, a many-to-one, one-to-one or embedded attribute
 * contributes the columns of the nested model, and a collection attribute becomes a collection
 * of the plan. Collections are supported only as direct properties of the root model, and must
 * belong to exactly one section.
 */
final class ModelPlanDeriver {

    private static final Set<PersistentAttributeType> NESTED = Set.of(PersistentAttributeType.MANY_TO_ONE,
            PersistentAttributeType.ONE_TO_ONE, PersistentAttributeType.EMBEDDED);

    private final List<String> problems = new ArrayList<>();

    private ModelPlanDeriver() {
    }

    static <T> ProjectionPlan<T> derive(Metamodel metamodel, Class<?> model, Class<T> type) {
        EntityType<T> entity;
        try {
            entity = metamodel.entity(type);
        }
        catch (IllegalArgumentException e) {
            throw new InvalidProjectionPlanException(type, List.of("the type is not an entity"));
        }
        ModelPlanDeriver deriver = new ModelPlanDeriver();
        String idPath = entity.getSingularAttributes().stream()
                .filter(SingularAttribute::isId)
                .map(Attribute::getName)
                .findFirst()
                .orElse(null);
        if (idPath == null) {
            throw new InvalidProjectionPlanException(type, List.of("the entity has no single identifier attribute"));
        }
        ProjectionPlan<T> plan = ProjectionPlan.of(type, idPath);
        deriver.addColumns(model, entity, "", List.of(List.of()), plan.columns(), plan);
        if (!deriver.problems.isEmpty()) {
            throw new InvalidProjectionPlanException(type, deriver.problems);
        }
        return plan;
    }

    /**
     * @param model the model whose properties are added
     * @param type the managed type the model is read from
     * @param prefix path of {@code type} from the columns' owner
     * @param required alternatives of sections required to read the model (any of them), each
     *            relative to the ancestors of {@code columns}; {@code null} for full only
     * @param columns where columns are added
     * @param plan where collections are added, or {@code null} if they are not supported here
     */
    private void addColumns(Class<?> model, ManagedType<?> type, String prefix, List<List<ModelPart>> required,
            ProjectionColumns columns, ProjectionPlan<?> plan) {

        List<Field> properties = properties(model);
        boolean marked = properties.stream()
                .anyMatch(field -> annotation(model, field, ModelBasic.class) != null
                        || annotation(model, field, ModelSections.class) != null);

        for (Field field : properties) {
            String property = model.getSimpleName() + "." + field.getName();
            List<List<ModelPart>> propertyRequired = required(model, field, marked, required);
            ModelSource source = annotation(model, field, ModelSource.class);
            String sourcePath = source != null ? source.value() : field.getName();

            Attribute<?, ?> attribute = resolve(type, sourcePath, property);
            if (attribute == null) {
                continue;
            }
            String path = prefix + sourcePath;
            PersistentAttributeType kind = attribute.getPersistentAttributeType();
            if (kind == PersistentAttributeType.BASIC) {
                add(columns, propertyRequired, path);
            }
            else if (NESTED.contains(kind)) {
                ManagedType<?> target = (ManagedType<?>) ((SingularAttribute<?, ?>) attribute).getType();
                Class<?> nested = nestedModel(field);
                if (nested != null) {
                    addColumns(nested, target, path + ".", propertyRequired, columns, null);
                }
                else {
                    problems.add("property " + property + ": cannot determine the model type");
                }
            }
            else {
                addCollection(model, field, property, sourcePath, (PluralAttribute<?, ?, ?>) attribute, propertyRequired, plan);
            }
        }
    }

    private void addCollection(Class<?> model, Field field, String property, String attribute,
            PluralAttribute<?, ?, ?> plural, List<List<ModelPart>> required, ProjectionPlan<?> plan) {

        ModelSections sections = annotation(model, field, ModelSections.class);
        if (plan == null || attribute.contains(".")) {
            problems.add("property " + property + ": collections are only supported as direct properties of the root model");
        }
        else if (required == null || sections == null || sections.value().length != 1) {
            problems.add("property " + property + ": a collection must belong to exactly one section");
        }
        else if (!(plural.getElementType() instanceof ManagedType<?> element)) {
            problems.add("property " + property + ": collection '" + attribute + "' does not contain entities or embeddables");
        }
        else {
            Class<?> elementModel = elementModel(field);
            if (elementModel == null) {
                problems.add("property " + property + ": cannot determine the element model of the map or collection");
                return;
            }
            plan.collection(sections.value()[0], attribute,
                    columns -> addColumns(elementModel, element, "", List.of(List.of()), columns, null));
        }
    }

    /**
     * Combines the sections required by the enclosing properties with those of a property.
     */
    private static List<List<ModelPart>> required(Class<?> model, Field field, boolean marked,
            List<List<ModelPart>> required) {
        if (required == null) {
            return null;
        }
        if (!marked || annotation(model, field, ModelBasic.class) != null) {
            return required;
        }
        ModelSections sections = annotation(model, field, ModelSections.class);
        if (sections == null) {
            // Unmarked property of a marked model: written only without selection
            return null;
        }
        if (sections.value().length == 0) {
            return required;
        }
        List<List<ModelPart>> combined = new ArrayList<>();
        for (List<ModelPart> alternative : required) {
            for (ModelPart part : sections.value()) {
                List<ModelPart> parts = new ArrayList<>(alternative);
                parts.add(part);
                combined.add(parts);
            }
        }
        return combined;
    }

    private static void add(ProjectionColumns columns, List<List<ModelPart>> required, String path) {
        if (required == null) {
            columns.fullOnly(path);
        }
        else {
            required.forEach(parts -> columns.sections(parts, path));
        }
    }

    private Attribute<?, ?> resolve(ManagedType<?> type, String path, String property) {
        ManagedType<?> current = type;
        String[] segments = path.split("\\.", -1);
        for (int i = 0; i < segments.length; i++) {
            Attribute<?, ?> attribute;
            try {
                attribute = current.getAttribute(segments[i]);
            }
            catch (IllegalArgumentException e) {
                problems.add("property " + property + ": unknown attribute '" + segments[i] + "' of "
                        + current.getJavaType().getSimpleName() + " (use @ModelSource if the name differs)");
                return null;
            }
            if (i == segments.length - 1) {
                return attribute;
            }
            if (!(NESTED.contains(attribute.getPersistentAttributeType())
                    && ((SingularAttribute<?, ?>) attribute).getType() instanceof ManagedType<?> target)) {
                problems.add("property " + property + ": source '" + path
                        + "' can only cross many-to-one, one-to-one or embedded attributes");
                return null;
            }
            current = target;
        }
        return null;
    }

    /**
     * @return the non-static fields of the model and its superclasses not ignored by Jackson,
     *         superclass fields first
     */
    private static List<Field> properties(Class<?> model) {
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        for (Class<?> type = model; type != null && type != Object.class && type != Record.class; type = type.getSuperclass()) {
            hierarchy.push(type);
        }
        List<Field> fields = new ArrayList<>();
        for (Class<?> type : hierarchy) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()
                        && annotation(model, field, JsonIgnore.class) == null) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /**
     * Finds an annotation on the field or, as the serialization does, on its accessor.
     */
    private static <A extends Annotation> A annotation(Class<?> model, Field field, Class<A> type) {
        A annotation = field.getAnnotation(type);
        if (annotation != null) {
            return annotation;
        }
        String name = field.getName();
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String accessor : List.of("get" + capitalized, "is" + capitalized, name)) {
            AnnotatedElement method = method(model, accessor);
            if (method != null && method.getAnnotation(type) != null) {
                return method.getAnnotation(type);
            }
        }
        return null;
    }

    private static Method method(Class<?> model, String name) {
        try {
            return model.getMethod(name);
        }
        catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * @return the model held by a property, unwrapping {@link Optional}
     */
    private static Class<?> nestedModel(Field field) {
        if (field.getType() == Optional.class) {
            return typeArgument(field.getGenericType(), 0);
        }
        return field.getType();
    }

    /**
     * @return the value type of a map, or the element type of a collection or array
     */
    private static Class<?> elementModel(Field field) {
        Class<?> type = field.getType();
        if (type.isArray()) {
            return type.getComponentType();
        }
        if (Map.class.isAssignableFrom(type)) {
            return typeArgument(field.getGenericType(), 1);
        }
        if (Collection.class.isAssignableFrom(type)) {
            return typeArgument(field.getGenericType(), 0);
        }
        return null;
    }

    private static Class<?> typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[index] instanceof Class<?> argument) {
            return argument;
        }
        return null;
    }
}
