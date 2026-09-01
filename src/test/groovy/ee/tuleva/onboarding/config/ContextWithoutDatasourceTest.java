package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(ContextWithoutDatasourceTest.TestConfig.class)
class ContextWithoutDatasourceTest {

  @Autowired ApplicationContext context;

  @Test
  void contextsLoadedWithoutSpringBootConfigurationStartWithoutADatabase() {
    assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
  }

  @Configuration
  static class TestConfig {}
}
