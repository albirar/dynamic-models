package cat.albirar.dynmod.filtering.web;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import cat.albirar.dynmod.filtering.model.InvalidModelSelectionException;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Finds the {@link ModelFiltering} configuration of an endpoint and resolves the selection of
 * a request. The result is stored in the request under {@link #SELECTION_ATTRIBUTE}, so it is
 * parsed once and shared by {@link ModelSelectionInterceptor}, {@link ModelSelectionArgumentResolver}
 * and {@link ModelFilteringAdvice}.
 */
public class ModelSelectionResolver {

    /**
     * Request attribute holding the resolved {@link ModelSelection} of an annotated endpoint.
     */
    public static final String SELECTION_ATTRIBUTE = ModelSelectionResolver.class.getName() + ".selection";

    private final Map<Endpoint, Optional<ModelFiltering>> annotations = new ConcurrentHashMap<>();

    /**
     * Finds the annotation of an endpoint. A method annotation replaces the class annotation.
     *
     * @param controller the controller class
     * @param method the handler method, or {@code null}
     * @return the annotation, or empty if the endpoint is not filtered
     */
    public Optional<ModelFiltering> findAnnotation(Class<?> controller, Method method) {
        return annotations.computeIfAbsent(new Endpoint(controller, method), endpoint -> {
            ModelFiltering annotation = method != null
                    ? AnnotatedElementUtils.findMergedAnnotation(method, ModelFiltering.class)
                    : null;
            if (annotation == null) {
                annotation = AnnotatedElementUtils.findMergedAnnotation(controller, ModelFiltering.class);
            }
            return Optional.ofNullable(annotation);
        });
    }

    /**
     * Returns the selection already stored in the request or, if there is none, reads it from
     * the query parameter or, when the parameter is absent, from the header, and stores it.
     *
     * @param request the request
     * @param filtering the endpoint configuration
     * @return the selection; {@link ModelSelection#full()} when the request has none
     * @throws ModelFilteringException if the selection is invalid or has several values
     */
    public ModelSelection resolve(HttpServletRequest request, ModelFiltering filtering) {
        if (request.getAttribute(SELECTION_ATTRIBUTE) instanceof ModelSelection selection) {
            return selection;
        }
        ModelSelection selection = parse(new ServletServerHttpRequest(request), filtering);
        request.setAttribute(SELECTION_ATTRIBUTE, selection);
        return selection;
    }

    private static ModelSelection parse(ServerHttpRequest request, ModelFiltering filtering) {
        String name = filtering.queryParam();
        List<String> values = queryParameterValues(request, name);
        if (values == null) {
            name = filtering.header();
            values = request.getHeaders().get(name);
        }
        if (values == null || values.isEmpty()) {
            return ModelSelection.full();
        }
        if (values.size() > 1) {
            throw new ModelFilteringException("Model selection '" + name + "' must have a single value");
        }
        try {
            return ModelSelection.parse(values.get(0));
        }
        catch (InvalidModelSelectionException e) {
            throw new ModelFilteringException(e.getMessage(), e);
        }
    }

    private static List<String> queryParameterValues(ServerHttpRequest request, String name) {
        List<String> values = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().get(name);
        if (values == null) {
            return null;
        }
        // A parameter without '=' ("?m") is reported as null: treat it as an empty selection
        return values.stream()
                .map(value -> value == null ? "" : UriUtils.decode(value, StandardCharsets.UTF_8))
                .toList();
    }

    private record Endpoint(Class<?> controller, Method method) {
    }
}
