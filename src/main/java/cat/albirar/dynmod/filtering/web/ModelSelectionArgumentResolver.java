package cat.albirar.dynmod.filtering.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import cat.albirar.dynmod.filtering.model.ModelSelection;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Supplies the request selection to handler parameters of type {@link ModelSelection}, so the
 * data layer can load only the selected sections. Endpoints without {@link ModelFiltering}
 * receive {@link ModelSelection#full()}, since their response is never filtered.
 */
public class ModelSelectionArgumentResolver implements HandlerMethodArgumentResolver {

    private final ModelSelectionResolver resolver;

    public ModelSelectionArgumentResolver(ModelSelectionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == ModelSelection.class;
    }

    @Override
    public ModelSelection resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return ModelSelection.full();
        }
        return resolver.findAnnotation(parameter.getContainingClass(), parameter.getMethod())
                .map(filtering -> resolver.resolve(request, filtering))
                .orElse(ModelSelection.full());
    }
}
