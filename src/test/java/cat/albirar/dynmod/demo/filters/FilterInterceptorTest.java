package cat.albirar.dynmod.demo.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import cat.albirar.dynmod.demo.models.filters.FiltersModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for FilterInterceptor.
 */
class FilterInterceptorTest {

    private FilterInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new FilterInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @AfterEach
    void tearDown() {
        FilterContext.clear();
    }

    @Test
    void testNoFiltering_WhenNoAnnotation() throws Exception {
        // Given: Handler without @EnableModelFiltering
        HandlerMethod handlerMethod = createHandlerMethodWithoutAnnotation();

        // When: Interceptor processes the request
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then: Filtering should not be enabled
        assertTrue(result);
        assertFalse(FilterContext.isFilteringEnabled());
        assertTrue(FilterContext.getFilters().isEmpty());
    }

    @Test
    void testFilteringEnabled_WithParameter() throws Exception {
        // Given: Handler with @EnableModelFiltering and parameter "m"
        HandlerMethod handlerMethod = createHandlerMethodWithAnnotation();
        when(request.getParameter("m")).thenReturn("B,P");

        // When: Interceptor processes the request
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then: Filtering should be enabled with Brand and Parts filters
        assertTrue(result);
        assertTrue(FilterContext.isFilteringEnabled());
        Set<FiltersModel> filters = FilterContext.getFilters();
        assertEquals(2, filters.size());
        assertTrue(filters.contains(FiltersModel.Brand));
        assertTrue(filters.contains(FiltersModel.Parts));
    }

    @Test
    void testFilteringEnabled_WithHeader() throws Exception {
        // Given: Handler with @EnableModelFiltering and header "x-filter-model"
        HandlerMethod handlerMethod = createHandlerMethodWithAnnotation();
        when(request.getParameter("m")).thenReturn(null);
        when(request.getHeader("x-filter-model")).thenReturn("M,E");

        // When: Interceptor processes the request
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then: Filtering should be enabled with Market and Economic filters
        assertTrue(result);
        assertTrue(FilterContext.isFilteringEnabled());
        Set<FiltersModel> filters = FilterContext.getFilters();
        assertEquals(2, filters.size());
        assertTrue(filters.contains(FiltersModel.Market));
        assertTrue(filters.contains(FiltersModel.Economic));
    }

    @Test
    void testParameterTakesPrecedence_OverHeader() throws Exception {
        // Given: Handler with @EnableModelFiltering, both parameter and header
        HandlerMethod handlerMethod = createHandlerMethodWithAnnotation();
        when(request.getParameter("m")).thenReturn("B");
        when(request.getHeader("x-filter-model")).thenReturn("M");

        // When: Interceptor processes the request
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then: Parameter should take precedence
        assertTrue(result);
        assertTrue(FilterContext.isFilteringEnabled());
        Set<FiltersModel> filters = FilterContext.getFilters();
        assertEquals(1, filters.size());
        assertTrue(filters.contains(FiltersModel.Brand));
        assertFalse(filters.contains(FiltersModel.Market));
    }

    @Test
    void testNoFiltering_WhenNoParameterOrHeader() throws Exception {
        // Given: Handler with @EnableModelFiltering but no parameter or header
        HandlerMethod handlerMethod = createHandlerMethodWithAnnotation();
        when(request.getParameter("m")).thenReturn(null);
        when(request.getHeader("x-filter-model")).thenReturn(null);

        // When: Interceptor processes the request
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then: Filtering should not be enabled
        assertTrue(result);
        assertFalse(FilterContext.isFilteringEnabled());
    }

    @Test
    void testInvalidFilterCode_ThrowsException() throws Exception {
        // Given: Handler with @EnableModelFiltering and invalid filter code
        HandlerMethod handlerMethod = createHandlerMethodWithAnnotation();
        when(request.getParameter("m")).thenReturn("B,INVALID,E");

        // When: Interceptor processes the request
        // Then: Should throw ModelFilteringException
        assertThrows(ModelFilteringException.class, () -> {
            interceptor.preHandle(request, response, handlerMethod);
        });
    }

    @Test
    void testEmptyFilterValue_Ignored() throws Exception {
        // Given: Handler with @EnableModelFiltering and empty filter value
        HandlerMethod handlerMethod = createHandlerMethodWithAnnotation();
        when(request.getParameter("m")).thenReturn("B,,E");

        // When: Interceptor processes the request
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then: Empty values should be ignored
        assertTrue(result);
        assertTrue(FilterContext.isFilteringEnabled());
        Set<FiltersModel> filters = FilterContext.getFilters();
        assertEquals(2, filters.size());
        assertTrue(filters.contains(FiltersModel.Brand));
        assertTrue(filters.contains(FiltersModel.Economic));
    }

    @Test
    void testAfterCompletion_ClearsContext() throws Exception {
        // Given: Handler with @EnableModelFiltering and parameter
        HandlerMethod handlerMethod = createHandlerMethodWithAnnotation();
        when(request.getParameter("m")).thenReturn("B");

        // When: Interceptor processes the request
        interceptor.preHandle(request, response, handlerMethod);
        assertTrue(FilterContext.isFilteringEnabled());

        // And: After completion
        interceptor.afterCompletion(request, response, handlerMethod, null);

        // Then: Context should be cleared
        assertFalse(FilterContext.isFilteringEnabled());
        assertTrue(FilterContext.getFilters().isEmpty());
    }

    private HandlerMethod createHandlerMethodWithAnnotation() throws Exception {
        // Create a mock method with @EnableModelFiltering
        Method method = this.getClass().getMethod("dummyMethod");
        return new HandlerMethod(this, method);
    }

    private HandlerMethod createHandlerMethodWithoutAnnotation() throws Exception {
        // Create a mock method without @EnableModelFiltering
        Method method = this.getClass().getMethod("anotherDummyMethod");
        return new HandlerMethod(this, method);
    }

    @EnableModelFiltering
    public void dummyMethod() {
        // Dummy method with annotation
    }

    public void anotherDummyMethod() {
        // Dummy method without annotation
    }
}
