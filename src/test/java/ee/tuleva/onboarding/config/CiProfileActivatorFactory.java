package ee.tuleva.onboarding.config;

import java.util.List;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Automatically activates "ci" profile when CI=true environment variable is set. This enables all
 * tests to automatically use PostgreSQL via Testcontainers in CI without any manual configuration.
 *
 * <p>The actual PostgreSQL container setup is handled by {@link TestcontainersConfiguration} using
 * Spring Boot's {@code @ServiceConnection} for idiomatic container management.
 */
public class CiProfileActivatorFactory implements ContextCustomizerFactory {

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return new CiProfileActivator();
  }

  private static class CiProfileActivator implements ContextCustomizer {

    @Override
    public void customizeContext(
        ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
      String ciEnv = System.getenv("CI");
      if ("true".equalsIgnoreCase(ciEnv)) {
        context.getEnvironment().addActiveProfile("ci");

        // Configure Flyway to exclude H2-specific migrations when using PostgreSQL
        System.setProperty("spring.flyway.locations", "classpath:/db/migration,classpath:/db/dev");

        // Register TestcontainersConfiguration so Spring can auto-configure datasource
        // via @ServiceConnection when the "ci" profile is active
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context.getBeanFactory();
        if (!registry.containsBeanDefinition("testcontainersConfiguration")) {
          registry.registerBeanDefinition(
              "testcontainersConfiguration",
              new RootBeanDefinition(TestcontainersConfiguration.class));
        }
      }
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof CiProfileActivator;
    }

    @Override
    public int hashCode() {
      return CiProfileActivator.class.hashCode();
    }
  }
}
