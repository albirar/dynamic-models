package cat.albirar.dynmod.demo.filters;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import cat.albirar.dynmod.demo.models.filters.FilteredView;
import cat.albirar.dynmod.demo.models.filters.FiltersModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom Jackson serializer to apply filtering based on @FilteredView annotations.
 * This serializer is applied recursively to all objects during serialization.
 */
@Slf4j
public class FilteredViewSerializer extends StdSerializer<Object> {

    // Package prefixes that should not be filtered (JDK, Spring, etc.)
    private static final Set<String> EXCLUDED_PACKAGES = Set.of(
        "java.",
        "javax.",
        "jakarta.",
        "org.springframework.",
        "org.hibernate.",
        "com.fasterxml.jackson.",
        "lombok."
    );

    public FilteredViewSerializer() {
        super(Object.class);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        LOGGER.debug("Serialitzar: {}", value);
        // If filtering is not enabled or no filters are set, serialize the full object
        if (!FilterContext.isFilteringEnabled() || FilterContext.getFilters().isEmpty()) {
            LOGGER.debug("Utilitza el de per defecte!");
            provider.defaultSerializeValue(value, gen);
            return;
        }

        // Check if we're already serializing to prevent infinite recursion
        if (FilterContext.isSerializing()) {
            LOGGER.debug("No serialitzar per evitar recursió");
            provider.defaultSerializeValue(value, gen);
            return;
        }

        if (value == null) {
            LOGGER.debug("Value és null, només es mostra 'null'");
            gen.writeNull();
            return;
        }

        Class<?> clazz = value.getClass();
        
        // Skip filtering for JDK and other excluded packages
        if (isExcludedPackage(clazz)) {
            LOGGER.debug("Exclòs per paquet: {}", clazz.getName());
            provider.defaultSerializeValue(value, gen);
            return;
        }

        // Check if this class or any of its fields have @FilteredView annotation
        if (!hasFilteredViewAnnotation(clazz)) {
            LOGGER.debug("Exclòs per no tenir anotacions");
            provider.defaultSerializeValue(value, gen);
            return;
        }

        // Set the serializing flag to prevent recursion
        FilterContext.setSerializing(true);
        
        try {
            // Start the object
            gen.writeStartObject();

            // Iterate through all fields (including inherited ones)
            for (Field field : getAllFields(clazz)) {
                try {
                    field.setAccessible(true);
                    FilteredView filterAnnotation = field.getAnnotation(FilteredView.class);

                    // If the field has @FilteredView, check if it should be included
                    if (filterAnnotation != null) {
                        boolean shouldInclude = Arrays.stream(filterAnnotation.filters())
                            .anyMatch(FilterContext.getFilters()::contains);

                        if (!shouldInclude) {
                            continue; // Skip this field
                        }
                    }

                    // Get the field value
                    Object fieldValue = field.get(value);
                    if (fieldValue == null) {
                        continue; // Skip null values (consistent with @JsonInclude(NON_ABSENT))
                    }

                    // For nested objects, we always use default serialization
                    // The filtering will be applied automatically by Jackson if the nested object
                    // has a registered serializer (which it does for our model classes)
                    gen.writeFieldName(field.getName());
                    provider.defaultSerializeValue(fieldValue, gen);

                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Error accessing field: " + field.getName(), e);
                }
            }

            // End the object
            gen.writeEndObject();
        } finally {
            // Always reset the serializing flag
            FilterContext.setSerializing(false);
        }
    }

    /**
     * Checks if a class is from an excluded package.
     */
    private boolean isExcludedPackage(Class<?> clazz) {
        String className = clazz.getName();
        for (String prefix : EXCLUDED_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a class or any of its fields have @FilteredView annotation.
     */
    private boolean hasFilteredViewAnnotation(Class<?> clazz) {
        // Check if the class itself has the annotation
        if (clazz.getAnnotation(FilteredView.class) != null) {
            return true;
        }

        // Check all fields (including inherited ones)
        for (Field field : getAllFields(clazz)) {
            if (field.getAnnotation(FilteredView.class) != null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Recursively gets all fields of a class, including those from superclasses.
     */
    private Field[] getAllFields(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) {
            return new Field[0];
        }
        Field[] currentFields = clazz.getDeclaredFields();
        Field[] parentFields = getAllFields(clazz.getSuperclass());
        Field[] allFields = Arrays.copyOf(currentFields, currentFields.length + parentFields.length);
        System.arraycopy(parentFields, 0, allFields, currentFields.length, parentFields.length);
        return allFields;
    }
}
