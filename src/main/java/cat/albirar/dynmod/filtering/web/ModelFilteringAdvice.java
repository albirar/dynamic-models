package cat.albirar.dynmod.filtering.web;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import cat.albirar.dynmod.filtering.jackson.ModelFilterProvider;
import cat.albirar.dynmod.filtering.model.InvalidModelSelectionException;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import tools.jackson.databind.ser.FilterProvider;

/**
 * Applies the model selection of endpoints annotated with {@link ModelFiltering}.
 * <p>
 * {@link #beforeBodyWrite} reads the selection from the query parameter or, when the parameter
 * is absent, from the header; it validates it, adds the header to {@code Vary} and stores the
 * selection as a request attribute. {@link #determineWriteHints}, which Spring calls
 * afterwards without the request, turns it into a per-response {@link FilterProvider} hint.
 * Without selection the body is written as usual.
 */
@ControllerAdvice
public class ModelFilteringAdvice implements ResponseBodyAdvice<Object> {

    static final String SELECTION_ATTRIBUTE = ModelFilteringAdvice.class.getName() + ".selection";
    private static final String FILTER_PROVIDER_HINT = FilterProvider.class.getName();

    private final FilterProvider delegate;
    private final Map<Endpoint, Optional<ModelFiltering>> annotations = new ConcurrentHashMap<>();

    /**
     * @param delegate provider for filter ids other than the model filter, or {@code null}
     */
    public ModelFilteringAdvice(FilterProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return JacksonJsonHttpMessageConverter.class.isAssignableFrom(converterType)
                && findAnnotation(returnType).isPresent();
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {

        ModelFiltering filtering = findAnnotation(returnType).orElseThrow();

        // The header can change the response whenever the query parameter is absent
        addVary(response.getHeaders(), filtering.header());
        if (isError(response) || !(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }

        String name = filtering.queryParam();
        List<String> values = queryParameterValues(request, name);
        if (values == null) {
            name = filtering.header();
            values = request.getHeaders().get(name);
        }
        if (values == null || values.isEmpty()) {
            return body;
        }
        if (values.size() > 1) {
            throw new ModelFilteringException("Model selection '" + name + "' must have a single value");
        }
        try {
            servletRequest.getServletRequest().setAttribute(SELECTION_ATTRIBUTE, ModelSelection.parse(values.get(0)));
        }
        catch (InvalidModelSelectionException e) {
            throw new ModelFilteringException(e.getMessage(), e);
        }
        return body;
    }

    @Override
    public Map<String, Object> determineWriteHints(Object body, MethodParameter returnType,
            MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType) {

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null
                || !(attributes.getAttribute(SELECTION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof ModelSelection selection)) {
            return null;
        }
        return Map.of(FILTER_PROVIDER_HINT, new ModelFilterProvider(selection, delegate));
    }

    private Optional<ModelFiltering> findAnnotation(MethodParameter returnType) {
        Method method = returnType.getMethod();
        Class<?> controller = returnType.getContainingClass();
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

    private static void addVary(HttpHeaders headers, String name) {
        List<String> current = headers.get(HttpHeaders.VARY);
        if (current != null) {
            boolean present = current.stream()
                    .flatMap(value -> Arrays.stream(value.split(",")))
                    .map(String::trim)
                    .anyMatch(value -> value.equals("*") || value.equalsIgnoreCase(name));
            if (present) {
                return;
            }
        }
        headers.add(HttpHeaders.VARY, name);
    }

    private static boolean isError(ServerHttpResponse response) {
        return response instanceof ServletServerHttpResponse servletResponse
                && servletResponse.getServletResponse().getStatus() >= 400;
    }

    private record Endpoint(Class<?> controller, Method method) {
    }
}
