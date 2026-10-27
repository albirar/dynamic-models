package cat.albirar.dynmod.filtering.model;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a property that is always included in a filtered response.
 * Must not be combined with {@link ModelSections} on the same property.
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, METHOD })
public @interface ModelBasic {
}
