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
 * <p>
 * The selection can be sent as a query parameter or as a header. When both are present the
 * query parameter is used and the header is ignored.
 */
@Documented
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface ModelFiltering {

    String DEFAULT_QUERY_PARAM = "m";
    String DEFAULT_HEADER = "X-Model-Sections";

    /**
     * Name of the query parameter carrying the selection.
     */
    String queryParam() default DEFAULT_QUERY_PARAM;

    /**
     * Name of the header carrying the selection.
     */
    String header() default DEFAULT_HEADER;
}
