package cat.albirar.dynmod.filtering.jackson;

import com.fasterxml.jackson.annotation.JsonFilter;

import cat.albirar.dynmod.filtering.model.ModelBasic;
import cat.albirar.dynmod.filtering.model.ModelSections;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.NopAnnotationIntrospector;

/**
 * Assigns {@link ModelFilteringModule#FILTER_ID} to beans that have at least one marked
 * member, and rejects inconsistent configurations.
 */
@SuppressWarnings("serial")
class ModelFilteringAnnotationIntrospector extends NopAnnotationIntrospector {

    @Override
    public Object findFilterId(MapperConfig<?> config, Annotated annotated) {
        if (!(annotated instanceof AnnotatedClass annotatedClass)) {
            return null;
        }
        boolean marked = false;
        for (AnnotatedMember member : annotatedClass.fields()) {
            marked |= isMarked(annotatedClass, member);
        }
        for (AnnotatedMember member : annotatedClass.memberMethods()) {
            marked |= isMarked(annotatedClass, member);
        }
        if (!marked) {
            return null;
        }
        if (annotatedClass.hasAnnotation(JsonFilter.class)) {
            throw new IllegalStateException("Bean " + annotatedClass.getRawType().getName()
                    + " has model filtering marks and its own @JsonFilter; use only one of them");
        }
        return ModelFilteringModule.FILTER_ID;
    }

    private static boolean isMarked(AnnotatedClass owner, AnnotatedMember member) {
        boolean basic = member.hasAnnotation(ModelBasic.class);
        boolean sections = member.hasAnnotation(ModelSections.class);
        if (basic && sections) {
            throw new IllegalStateException("Member " + owner.getRawType().getName() + "." + member.getName()
                    + " cannot be annotated with both @ModelBasic and @ModelSections");
        }
        return basic || sections;
    }
}
