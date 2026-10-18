package cat.albirar.dynmod.demo.filters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import cat.albirar.dynmod.demo.models.filters.FiltersModel;

/**
 * Interceptor to read the filter parameter or header and enable filtering for endpoints
 * marked with @EnableModelFiltering.
 */
@Slf4j
public class FilterInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LOGGER.debug("Prehandle {}", handler);
        // Only apply if the handler is a HandlerMethod
        if (!(handler instanceof HandlerMethod)) {
            FilterContext.clear();
            LOGGER.debug("Sortida sense filtratge perquè no és pas un mètode");
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        boolean hasAnnotation = handlerMethod.getMethodAnnotation(EnableModelFiltering.class) != null ||
                               (handlerMethod.getBeanType() != null && 
                                handlerMethod.getBeanType().getAnnotation(EnableModelFiltering.class) != null);

        if (!hasAnnotation) {
            FilterContext.clear();
            LOGGER.debug("Sortida sense filtratge perquè no és hi ha anotació");
            return true;
        }

        // Read the parameter or header
        String filterParam = request.getParameter("m");
        String filterHeader = request.getHeader("x-filter-model");

        String filterValue = filterParam != null ? filterParam : filterHeader;
        LOGGER.debug("Filtratge: param: {}; header: {}, filterValue: {}", filterParam, filterHeader, filterValue);
        if (filterValue != null && !filterValue.isEmpty()) {
            Set<FiltersModel> filters = new HashSet<>();
            for (String code : filterValue.split(",")) {
                code = code.trim();
                if (!code.isEmpty()) {
                    try {
                        FiltersModel filter = FiltersModel.fromCode(code);
                        filters.add(filter);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid filter code '{}' in parameter or header. Ignoring.", code);
                    }
                }
            }
            FilterContext.setFilters(filters);
            FilterContext.setFilteringEnabled(true);
            LOGGER.debug("El filtre s'ha activat amb {}", filters);
        } else {
            LOGGER.debug("El filtre no s'ha activat!");
            FilterContext.setFilteringEnabled(false);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        FilterContext.clear();
    }
}
