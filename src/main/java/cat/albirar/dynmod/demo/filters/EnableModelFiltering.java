package cat.albirar.dynmod.demo.filters;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable model filtering for endpoints or controllers.
 * Only endpoints marked with this annotation will apply the filtering.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableModelFiltering {
}
