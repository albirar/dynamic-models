package cat.albirar.dynmod.filtering.web;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Opts a controller or endpoint into model filtering. A method annotation replaces the
 * class annotation completely.
 */
@Documented
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface ModelFiltering {

    ModelFilterSource source() default ModelFilterSource.QUERY_PARAM;

    /**
     * Name of the query parameter or header; empty to use the source's default name.
     */
    String name() default "";
}
