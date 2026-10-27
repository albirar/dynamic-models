package cat.albirar.dynmod.filtering.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class ModelFilteringAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ModelFilteringAutoConfiguration.class));

    @Test
    void disabledWithoutProperty() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ModelFilteringAdvice.class));
    }

    @Test
    void disabledWhenPropertyIsFalse() {
        runner.withPropertyValues("models.filtratge=false")
                .run(context -> assertThat(context).doesNotHaveBean(ModelFilteringAdvice.class));
    }

    @Test
    void enabledWhenPropertyIsTrue() {
        runner.withPropertyValues("models.filtratge=true")
                .run(context -> assertThat(context).hasSingleBean(ModelFilteringAdvice.class));
    }

    @Test
    void disabledOutsideWebApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ModelFilteringAutoConfiguration.class))
                .withPropertyValues("models.filtratge=true")
                .run(context -> assertThat(context).doesNotHaveBean(ModelFilteringAdvice.class));
    }
}
