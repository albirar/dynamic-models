package cat.albirar.dynmod.demo.filters;

import java.util.HashSet;
import java.util.Set;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import cat.albirar.dynmod.demo.models.filters.FiltersModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

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

        LOGGER.debug("Has annotation: {}", hasAnnotation);
        
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
            StringBuilder invalidCodes = new StringBuilder();
            for (String code : filterValue.split(",")) {
                code = code.trim();
                if (!code.isEmpty()) {
                    try {
                        FiltersModel filter = FiltersModel.fromCode(code);
                        filters.add(filter);
                    } catch (IllegalArgumentException e) {
                        if (invalidCodes.length() > 0) {
                            invalidCodes.append(", ");
                        }
                        invalidCodes.append("'").append(code).append("'");
                    }
                }
            }
            
            // If there are invalid codes, throw exception
            if (invalidCodes.length() > 0) {
                throw new ModelFilteringException("Invalid filter code(s): " + invalidCodes.toString());
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
