package cat.albirar.dynmod.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {

    @Bean
    public ApplicationProperties applicationProperties() {
        return new ApplicationProperties();
    }
}
