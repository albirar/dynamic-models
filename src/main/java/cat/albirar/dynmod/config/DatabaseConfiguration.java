package cat.albirar.dynmod.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "cat.albirar.dynmod.demo.repository")
public class DatabaseConfiguration {
}
