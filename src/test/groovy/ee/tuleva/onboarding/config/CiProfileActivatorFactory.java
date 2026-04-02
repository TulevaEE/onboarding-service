package ee.tuleva.onboarding.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Registers {@link TestcontainersConfiguration} when the "pg" profile is active
 * (SPRING_PROFILES_ACTIVE=pg,test).
 *
 * <p>Only registers when "pg" profile is active to avoid loading the class in environments without
 * Docker (e.g., CI with a service container, or local H2).
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
      if (!Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("pg")) {
        return;
      }
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
