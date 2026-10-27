package cat.albirar.dynmod.filtering.jackson;

import cat.albirar.dynmod.filtering.model.ModelSelection;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.FilterProvider;
import tools.jackson.databind.ser.PropertyFilter;

/**
 * Immutable filter provider resolving {@link ModelFilteringModule#FILTER_ID}. Other filter ids
 * are resolved by the delegate, if any, so application filters keep working.
 */
@SuppressWarnings("serial")
public final class ModelFilterProvider extends FilterProvider {

    private final PropertyFilter filter;
    private final FilterProvider delegate;

    /**
     * @param selection the sections to include, or {@code null} to include every property
     * @param delegate provider for other filter ids, or {@code null}
     */
    public ModelFilterProvider(ModelSelection selection, FilterProvider delegate) {
        this(new ModelSectionsPropertyFilter(selection), delegate);
    }

    private ModelFilterProvider(PropertyFilter filter, FilterProvider delegate) {
        this.filter = filter;
        this.delegate = delegate;
    }

    @Override
    public FilterProvider snapshot() {
        return delegate != null ? new ModelFilterProvider(filter, delegate.snapshot()) : this;
    }

    @Override
    public PropertyFilter findPropertyFilter(SerializationContext ctxt, Object filterId, Object valueToFilter) {
        if (ModelFilteringModule.FILTER_ID.equals(filterId)) {
            return filter;
        }
        return delegate != null ? delegate.findPropertyFilter(ctxt, filterId, valueToFilter) : null;
    }
}
