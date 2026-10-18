package cat.albirar.dynmod.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import cat.albirar.dynmod.demo.config.ApplicationProperties;

@SpringBootApplication(scanBasePackages = "cat.albirar.dynmod")
@EnableConfigurationProperties(ApplicationProperties.class)
@EnableJpaRepositories(basePackages = "cat.albirar.dynmod.demo.repository")
public class DemoApplication {

	@SuppressWarnings("null")
	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
