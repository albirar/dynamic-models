package cat.albirar.dynmod.demo.filters;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import cat.albirar.dynmod.demo.models.filters.FiltersModel;

/**
 * Thread-local context to store the active filters and filtering state.
 */
public class FilterContext {
    private static final ThreadLocal<Set<FiltersModel>> currentFilters = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> isFilteringEnabled = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Set<Class<?>>> serializingClasses = ThreadLocal.withInitial(HashSet::new);

    public static void setFilters(Set<FiltersModel> filters) {
        currentFilters.set(filters);
    }

    public static Set<FiltersModel> getFilters() {
        return currentFilters.get() != null ? currentFilters.get() : Collections.emptySet();
    }

    public static void setFilteringEnabled(boolean enabled) {
        isFilteringEnabled.set(enabled);
    }

    public static boolean isFilteringEnabled() {
        return isFilteringEnabled.get();
    }

    public static void addSerializingClass(Class<?> clazz) {
        serializingClasses.get().add(clazz);
    }

    public static void removeSerializingClass(Class<?> clazz) {
        serializingClasses.get().remove(clazz);
    }

    public static boolean isSerializing(Class<?> clazz) {
        return serializingClasses.get().contains(clazz);
    }

    public static void clear() {
        currentFilters.remove();
        isFilteringEnabled.remove();
        serializingClasses.remove();
    }
}
