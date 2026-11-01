package cat.albirar.dynmod.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cat.albirar.dynmod.demo.projection.VehicleProjection;
import cat.albirar.dynmod.filtering.jpa.ProjectionExecutor;
import jakarta.persistence.EntityManager;

@Configuration
public class ApplicationConfiguration {

    @Bean
    ApplicationProperties applicationProperties() {
        return new ApplicationProperties();
    }

    /**
     * The plan is derived from the beans and validated here: a model that does not match the
     * entities fails the startup instead of the first request.
     */
    @Bean
    ProjectionExecutor projectionExecutor(EntityManager entityManager) {
        ProjectionExecutor executor = new ProjectionExecutor(entityManager);
        VehicleProjection.plan(executor);
        return executor;
    }
}
