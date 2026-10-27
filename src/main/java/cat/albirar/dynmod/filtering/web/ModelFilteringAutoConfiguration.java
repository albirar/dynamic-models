package cat.albirar.dynmod.filtering.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.DispatcherServlet;

import cat.albirar.dynmod.filtering.jackson.ModelFilterProvider;
import cat.albirar.dynmod.filtering.jackson.ModelFilteringModule;
import tools.jackson.databind.json.JsonMapper;

/**
 * Model filtering for Spring MVC, enabled with {@code models.filtratge=true}.
 * <p>
 * The server JSON converter is replaced by one using a private copy of the application
 * {@link JsonMapper} with {@link ModelFilteringModule}. The shared mapper and the client
 * converters are not modified.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ DispatcherServlet.class, JsonMapper.class, JacksonJsonHttpMessageConverter.class })
@ConditionalOnBean(JsonMapper.class)
@ConditionalOnBooleanProperty(ModelFilteringAutoConfiguration.PROPERTY)
public class ModelFilteringAutoConfiguration {

    public static final String PROPERTY = "models.filtratge";

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    ServerHttpMessageConvertersCustomizer modelFilteringServerHttpMessageConvertersCustomizer(JsonMapper jsonMapper) {
        JsonMapper filteringMapper = jsonMapper.rebuild()
                .addModule(new ModelFilteringModule())
                .filterProvider(new ModelFilterProvider(null, jsonMapper.serializationConfig().getFilterProvider()))
                .build();
        return builder -> builder.withJsonConverter(new JacksonJsonHttpMessageConverter(filteringMapper));
    }

    @Bean
    ModelFilteringAdvice modelFilteringAdvice(JsonMapper jsonMapper) {
        return new ModelFilteringAdvice(jsonMapper.serializationConfig().getFilterProvider());
    }
}
