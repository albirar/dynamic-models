package cat.albirar.dynmod.demo.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import cat.albirar.dynmod.demo.filters.FilterInterceptor;
import cat.albirar.dynmod.demo.filters.FilteredViewPropertyFilter;

/**
 * Configuration for model filtering feature.
 * This configuration is only loaded if spring.application.models.filtered=true.
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.application.models.filtered",
    havingValue = "true"
)
public class FilterConfiguration {

    @Bean
    public FilterInterceptor filterInterceptor() {
        return new FilterInterceptor();
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer(FilterInterceptor filterInterceptor, ObjectMapper objectMapper) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(filterInterceptor);
            }
            
            @Override
            public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
                // Add our custom converter with the filtered ObjectMapper at the beginning
                converters.add(0, new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper));
            }
        };
    }
    
    @Bean
    public com.fasterxml.jackson.databind.ser.FilterProvider filterProvider() {
        FilteredViewPropertyFilter filter = new FilteredViewPropertyFilter();
        return new com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider()
            .addFilter("vehicleModelFilter", filter)
            .addFilter("brandFilter", filter)
            .addFilter("marketDataFilter", filter)
            .addFilter("vehiclePartFilter", filter);
    }
    
    @Bean
    @Primary
    public ObjectMapper objectMapper(com.fasterxml.jackson.databind.ser.FilterProvider filterProvider) {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .modules(new Jdk8Module(), new JavaTimeModule())
            .build();
        objectMapper.setFilterProvider(filterProvider);
        return objectMapper;
    }
}
