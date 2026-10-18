package cat.albirar.dynmod.demo.filters;

import java.util.Arrays;

import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;

import cat.albirar.dynmod.demo.models.filters.FilteredView;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom Jackson property filter to apply filtering based on @FilteredView annotations.
 */
@Slf4j
public class FilteredViewPropertyFilter extends SimpleBeanPropertyFilter {

    @Override
    protected boolean include(BeanPropertyWriter writer) {
        // If filtering is not enabled or no filters are set, include all properties
        if (!FilterContext.isFilteringEnabled() || FilterContext.getFilters().isEmpty()) {
            return true;
        }

        // Check if the property has @FilteredView annotation
        FilteredView filterAnnotation = writer.getAnnotation(FilteredView.class);
        if (filterAnnotation != null) {
            // Check if any of the filters match
            return Arrays.stream(filterAnnotation.filters())
                .anyMatch(FilterContext.getFilters()::contains);
        }

        // If no annotation, include the property (basic fields)
        return true;
    }

    @Override
    protected boolean include(PropertyWriter writer) {
        // If filtering is not enabled or no filters are set, include all properties
        if (!FilterContext.isFilteringEnabled() || FilterContext.getFilters().isEmpty()) {
            return true;
        }

        // Check if the property has @FilteredView annotation
        FilteredView filterAnnotation = writer.findAnnotation(FilteredView.class);
        if (filterAnnotation != null) {
            // Check if any of the filters match
            return Arrays.stream(filterAnnotation.filters())
                .anyMatch(FilterContext.getFilters()::contains);
        }

        // If no annotation, include the property (basic fields)
        return true;
    }
}
