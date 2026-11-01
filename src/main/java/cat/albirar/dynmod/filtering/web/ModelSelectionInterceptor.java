package cat.albirar.dynmod.filtering.web;

import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves and validates the selection of endpoints annotated with {@link ModelFiltering}
 * before the handler runs. An invalid selection is rejected with HTTP 400 without invoking
 * the controller, and a valid one is available to the data layer through a
 * {@link cat.albirar.dynmod.filtering.model.ModelSelection ModelSelection} parameter.
 */
public class ModelSelectionInterceptor implements HandlerInterceptor {

    private final ModelSelectionResolver resolver;

    public ModelSelectionInterceptor(ModelSelectionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            resolver.findAnnotation(handlerMethod.getBeanType(), handlerMethod.getMethod()).ifPresent(filtering -> {
                try {
                    resolver.resolve(request, filtering);
                }
                catch (ModelFilteringException e) {
                    // The rejection depends on the header too
                    response.addHeader(HttpHeaders.VARY, filtering.header());
                    throw e;
                }
            });
        }
        return true;
    }
}
