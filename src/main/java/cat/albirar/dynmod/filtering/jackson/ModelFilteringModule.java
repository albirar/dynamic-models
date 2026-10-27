package cat.albirar.dynmod.filtering.jackson;

import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson module that assigns the model filter to beans with {@code @ModelBasic} or
 * {@code @ModelSections} properties. Beans without marks are left untouched.
 * <p>
 * The mapper must have a {@link ModelFilterProvider} (or another provider resolving
 * {@link #FILTER_ID}); a provider without selection serializes every property.
 */
@SuppressWarnings("serial")
public class ModelFilteringModule extends SimpleModule {

    public static final String FILTER_ID = ModelFilteringModule.class.getName();

    public ModelFilteringModule() {
        super(ModelFilteringModule.class.getSimpleName());
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.insertAnnotationIntrospector(new ModelFilteringAnnotationIntrospector());
    }
}
