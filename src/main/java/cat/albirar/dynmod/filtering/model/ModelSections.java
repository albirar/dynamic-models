package cat.albirar.dynmod.filtering.model;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Associates a property with one or more optional sections. In a filtered response the
 * property is included when any of its sections is selected. Without sections the property
 * is included unconditionally; prefer {@link ModelBasic} for that case.
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, METHOD })
public @interface ModelSections {
    ModelPart[] value() default {};
}
