package ee.tuleva.onboarding.config;

import ee.tuleva.onboarding.config.SharedTestPostgres.TestDatabase;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Points every PostgreSQL-backed test context at its own database inside the shared server.
 *
 * <p>{@code customizeContext} only runs on a context cache miss, so each distinct cached context
 * gets exactly one database and cannot see rows written by another context or another fork.
 *
 * <p>Under the "pg" profile the server is a Testcontainers instance, under "ci" it is the CircleCI
 * service container. Without either profile tests run on H2 and this customizer does nothing.
 */
public class TestDatabaseCustomizerFactory implements ContextCustomizerFactory {

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return new TestDatabaseCustomizer();
  }

  private static class TestDatabaseCustomizer implements ContextCustomizer {

    @Override
    public void customizeContext(
        ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
      ConfigurableEnvironment environment = context.getEnvironment();
      List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
      if (!activeProfiles.contains("pg") && !activeProfiles.contains("ci")) {
        return;
      }
      if (!hasTestDatabaseConfiguration(environment)) {
        return;
      }
      TestDatabase database = SharedTestPostgres.createDatabase(environment);
      TestPropertyValues.of(
              "spring.datasource.url=" + database.url(),
              "spring.datasource.username=" + database.username(),
              "spring.datasource.password=" + database.password())
          .applyTo(environment);
    }

    // Contexts loaded by the plain @ContextConfiguration loaders never go through Spring Boot
    // config-data processing, so they never see application-test.yml and have nothing to point at
    // a database.
    private boolean hasTestDatabaseConfiguration(ConfigurableEnvironment environment) {
      return environment.containsProperty("spring.flyway.locations");
    }

    // equals and hashCode are part of Spring's context cache key and must stay identity-stable.
    // Carrying the per-context database name here would make every context cache-unique and blow
    // the suite up from ~50 contexts to one per test class.
    @Override
    public boolean equals(Object obj) {
      return obj instanceof TestDatabaseCustomizer;
    }

    @Override
    public int hashCode() {
      return TestDatabaseCustomizer.class.hashCode();
    }
  }
}
