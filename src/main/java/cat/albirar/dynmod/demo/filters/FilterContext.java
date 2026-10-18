package cat.albirar.dynmod.demo.filters;

import java.util.Collections;
import java.util.Set;

import cat.albirar.dynmod.demo.models.filters.FiltersModel;

/**
 * Thread-local context to store the active filters and filtering state.
 */
public class FilterContext {
    private static final ThreadLocal<Set<FiltersModel>> currentFilters = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> isFilteringEnabled = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isSerializing = ThreadLocal.withInitial(() -> false);

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

    public static void setSerializing(boolean serializing) {
        isSerializing.set(serializing);
    }

    public static boolean isSerializing() {
        return isSerializing.get();
    }

    public static void clear() {
        currentFilters.remove();
        isFilteringEnabled.remove();
        isSerializing.remove();
    }
}
