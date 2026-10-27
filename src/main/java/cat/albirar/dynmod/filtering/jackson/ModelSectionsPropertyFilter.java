package cat.albirar.dynmod.filtering.jackson;

import cat.albirar.dynmod.filtering.model.ModelBasic;
import cat.albirar.dynmod.filtering.model.ModelSections;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter;

/**
 * Immutable property filter for marked beans. Without selection every property is included;
 * with selection only basic properties and properties of a selected section are included.
 * Included properties are written by the original writer, so other Jackson rules still apply.
 */
class ModelSectionsPropertyFilter extends SimpleBeanPropertyFilter {

    private final ModelSelection selection;

    ModelSectionsPropertyFilter(ModelSelection selection) {
        this.selection = selection;
    }

    @Override
    protected boolean include(BeanPropertyWriter writer) {
        return include((PropertyWriter) writer);
    }

    @Override
    protected boolean include(PropertyWriter writer) {
        if (selection == null || writer.getAnnotation(ModelBasic.class) != null) {
            return true;
        }
        ModelSections sections = writer.getAnnotation(ModelSections.class);
        return sections != null && (sections.value().length == 0 || selection.includesAny(sections.value()));
    }
}
