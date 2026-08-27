package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextCustomizer;

class TestDatabaseCustomizerFactoryTest {

  private final TestDatabaseCustomizerFactory factory = new TestDatabaseCustomizerFactory();

  @Test
  void customizersForDifferentTestClassesShareOneContextCacheKey() {
    ContextCustomizer first = factory.createContextCustomizer(FirstTestClass.class, List.of());
    ContextCustomizer second = factory.createContextCustomizer(SecondTestClass.class, List.of());

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  private static class FirstTestClass {}

  private static class SecondTestClass {}
}
