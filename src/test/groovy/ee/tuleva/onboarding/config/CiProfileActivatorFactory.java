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
 * Registers Testcontainers configuration for all tests. The actual container is only created when
 * the "ci" profile is active (set via SPRING_PROFILES_ACTIVE=ci,test in CircleCI).
 *
 * <p>This factory is needed because {@link TestcontainersConfiguration} uses
 * {@code @TestConfiguration}, which is not component-scanned. The {@code @Profile("ci")} on
 * TestcontainersConfiguration ensures the PostgreSQL container is only created in CI.
 *
 * <p>The PostgreSQL container setup is handled by {@link TestcontainersConfiguration} using Spring
 * Boot's {@code @ServiceConnection} for idiomatic container management.
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
      // Register TestcontainersConfiguration (@TestConfiguration is not component-scanned)
      // The @Profile("ci") on the configuration ensures it only activates in CI
      BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context.getBeanFactory();
      if (!registry.containsBeanDefinition("testcontainersConfiguration")) {
        registry.registerBeanDefinition(
            "testcontainersConfiguration",
            new RootBeanDefinition(TestcontainersConfiguration.class));
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
