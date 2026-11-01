package cat.albirar.dynmod.filtering.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cat.albirar.dynmod.filtering.jpa.ProjectionPlan.CollectionPlan;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * Checks a {@link ProjectionPlan} against the JPA metamodel, with the same rules the
 * {@link ProjectionExecutor} relies on:
 * <ul>
 * <li>the root type is an entity;</li>
 * <li>every column path ends in a basic attribute, and its intermediate segments are
 * many-to-one or one-to-one associations;</li>
 * <li>every collection is a plural attribute of the root whose elements are entities or
 * embeddables, and its column paths follow the rules above from the element type.</li>
 * </ul>
 */
final class ProjectionPlanValidator {

    private static final Set<PersistentAttributeType> SINGLE_ASSOCIATIONS = Set.of(PersistentAttributeType.MANY_TO_ONE,
            PersistentAttributeType.ONE_TO_ONE);

    private ProjectionPlanValidator() {
    }

    /**
     * @throws InvalidProjectionPlanException with every problem found
     */
    static void validate(Metamodel metamodel, ProjectionPlan<?> plan) {
        ManagedType<?> root;
        try {
            root = metamodel.entity(plan.type());
        }
        catch (IllegalArgumentException e) {
            throw new InvalidProjectionPlanException(plan.type(), List.of("the type is not an entity"));
        }

        List<String> problems = new ArrayList<>();
        checkPath(root, plan.idPath(), "", problems);
        plan.columns().paths().forEach(path -> checkPath(root, path, "", problems));
        for (CollectionPlan collection : plan.collections()) {
            Attribute<?, ?> attribute = attribute(root, collection.attribute(), "", problems);
            if (attribute == null) {
                continue;
            }
            if (!(attribute instanceof PluralAttribute<?, ?, ?> plural)) {
                problems.add("collection '" + collection.attribute() + "' is not a collection attribute");
            }
            else if (!(plural.getElementType() instanceof ManagedType<?> element)) {
                problems.add("collection '" + collection.attribute() + "' does not contain entities or embeddables");
            }
            else {
                String prefix = collection.attribute() + ".";
                collection.columns().paths().forEach(path -> checkPath(element, path, prefix, problems));
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidProjectionPlanException(plan.type(), problems);
        }
    }

    private static void checkPath(ManagedType<?> type, String path, String prefix, List<String> problems) {
        String[] segments = path.split("\\.", -1);
        ManagedType<?> current = type;
        for (int i = 0; i < segments.length; i++) {
            Attribute<?, ?> attribute = attribute(current, segments[i], prefix + path, problems);
            if (attribute == null) {
                return;
            }
            boolean last = i == segments.length - 1;
            if (last) {
                if (attribute.getPersistentAttributeType() != PersistentAttributeType.BASIC) {
                    problems.add("path '" + prefix + path + "' must end in a basic attribute, but '" + segments[i]
                            + "' is " + describe(attribute));
                }
            }
            else if (SINGLE_ASSOCIATIONS.contains(attribute.getPersistentAttributeType())
                    && attribute instanceof SingularAttribute<?, ?> singular
                    && singular.getType() instanceof ManagedType<?> target) {
                current = target;
            }
            else {
                problems.add("path '" + prefix + path + "' can only cross many-to-one or one-to-one associations, but '"
                        + segments[i] + "' is " + describe(attribute));
                return;
            }
        }
    }

    private static Attribute<?, ?> attribute(ManagedType<?> type, String name, String path, List<String> problems) {
        try {
            return type.getAttribute(name);
        }
        catch (IllegalArgumentException e) {
            String where = path.isEmpty() ? "" : " in path '" + path + "'";
            problems.add("unknown attribute '" + name + "' of " + type.getJavaType().getSimpleName() + where);
            return null;
        }
    }

    private static String describe(Attribute<?, ?> attribute) {
        return attribute.getPersistentAttributeType().name().toLowerCase().replace('_', '-');
    }
}
