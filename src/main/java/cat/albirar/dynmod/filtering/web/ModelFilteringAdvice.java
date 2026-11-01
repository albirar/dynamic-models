package cat.albirar.dynmod.filtering.web;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.MethodParameter;
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

import cat.albirar.dynmod.filtering.jackson.ModelFilterProvider;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import tools.jackson.databind.ser.FilterProvider;

/**
 * Applies the model selection of endpoints annotated with {@link ModelFiltering}.
 * <p>
 * {@link #beforeBodyWrite} adds the header to {@code Vary} and takes the selection resolved by
 * {@link ModelSelectionInterceptor} before the handler ran (or resolves it now, if the
 * interceptor is not registered). For successful responses with a selection it stores the
 * selection to apply as a request attribute. {@link #determineWriteHints}, which Spring calls
 * afterwards without the request, turns it into a per-response {@link FilterProvider} hint.
 * Without selection the body is written as usual.
 */
@ControllerAdvice
public class ModelFilteringAdvice implements ResponseBodyAdvice<Object> {

    static final String APPLIED_SELECTION_ATTRIBUTE = ModelFilteringAdvice.class.getName() + ".selection";
    private static final String FILTER_PROVIDER_HINT = FilterProvider.class.getName();

    private final ModelSelectionResolver resolver;
    private final FilterProvider delegate;

    /**
     * @param resolver resolver shared with the interceptor and the argument resolver
     * @param delegate provider for filter ids other than the model filter, or {@code null}
     */
    public ModelFilteringAdvice(ModelSelectionResolver resolver, FilterProvider delegate) {
        this.resolver = resolver;
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

        ModelSelection selection = resolver.resolve(servletRequest.getServletRequest(), filtering);
        if (!selection.isFull()) {
            servletRequest.getServletRequest().setAttribute(APPLIED_SELECTION_ATTRIBUTE, selection);
        }
        return body;
    }

    @Override
    public Map<String, Object> determineWriteHints(Object body, MethodParameter returnType,
            MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType) {

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null
                || !(attributes.getAttribute(APPLIED_SELECTION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof ModelSelection selection)) {
            return null;
        }
        return Map.of(FILTER_PROVIDER_HINT, new ModelFilterProvider(selection, delegate));
    }

    private Optional<ModelFiltering> findAnnotation(MethodParameter returnType) {
        return resolver.findAnnotation(returnType.getContainingClass(), returnType.getMethod());
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
}
