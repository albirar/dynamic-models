package cat.albirar.dynmod.demo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.module.SimpleModule;

import cat.albirar.dynmod.demo.filters.FilterInterceptor;
import cat.albirar.dynmod.demo.filters.FilteredViewSerializer;
import cat.albirar.dynmod.demo.models.BrandBean;
import cat.albirar.dynmod.demo.models.MarketDataBean;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.models.VehiclePartBean;

/**
 * Configuration for model filtering feature.
 * This configuration is only loaded if spring.application.models.filtered=true.
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.application.models.filtered",
    havingValue = "true",
    matchIfMissing = false
)
public class FilterConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilder jacksonBuilder() {
        SimpleModule module = new SimpleModule();
        FilteredViewSerializer serializer = new FilteredViewSerializer();
        
        // Register the serializer only for the model classes that we need to filter
        module.addSerializer(VehicleModelBean.class, serializer);
        module.addSerializer(BrandBean.class, serializer);
        module.addSerializer(MarketDataBean.class, serializer);
        module.addSerializer(VehiclePartBean.class, serializer);
        
        return new Jackson2ObjectMapperBuilder()
            .modules(module);
    }

    @Bean
    public FilterInterceptor filterInterceptor() {
        return new FilterInterceptor();
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer(FilterInterceptor filterInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(filterInterceptor);
            }
        };
    }
}
