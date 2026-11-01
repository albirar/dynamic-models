package cat.albirar.dynmod.filtering.model;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Entity attribute a model property is read from, when its name differs from the property
 * name. Used to derive projection plans from the model; without it the property name is used.
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, METHOD })
public @interface ModelSource {

    /**
     * Attribute path relative to the entity of the model that declares the property. It may
     * cross many-to-one or one-to-one associations ({@code "brand.country"}).
     */
    String value();
}
