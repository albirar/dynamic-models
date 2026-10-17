package cat.albirar.dynmod.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "spring.application")
public class ApplicationProperties {
    private String name;
    private String version;
}
