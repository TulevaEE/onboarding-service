package ee.tuleva.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "spring.main.lazy-initialization=false")
@DirtiesContext(classMode = AFTER_CLASS)
class ApplicationWiringTest {

  @Autowired private ConfigurableApplicationContext context;

  @Test
  void everyEagerSingletonIsCreatedAtStartup() {
    ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
    List<String> eagerSingletons =
        Arrays.stream(beanFactory.getBeanDefinitionNames())
            .filter(name -> isEagerSingleton(beanFactory.getBeanDefinition(name)))
            .toList();

    assertThat(
            context.getEnvironment().getProperty("spring.main.lazy-initialization", Boolean.class))
        .isFalse();
    assertThat(beanFactory.getSingletonNames()).containsAll(eagerSingletons);
  }

  private static boolean isEagerSingleton(BeanDefinition definition) {
    return definition.isSingleton() && !definition.isAbstract() && !definition.isLazyInit();
  }
}
