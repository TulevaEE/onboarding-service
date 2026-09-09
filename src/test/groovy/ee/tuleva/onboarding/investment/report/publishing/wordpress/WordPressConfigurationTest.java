package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WordPressConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(WordPressConfiguration.class);

  @Test
  void contextFailsWhenPublishingIsEnabledWithoutWordPressConfiguration() {
    contextRunner
        .withPropertyValues("investment-report-publishing.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void wordPressClientIsCreatedWhenAllPropertiesArePresent() {
    contextRunner
        .withPropertyValues(
            "investment-report-publishing.enabled=true",
            "investment-report-publishing.wordpress.api-base=https://example.test/wp-json/wp/v2",
            "investment-report-publishing.wordpress.username=wp-user",
            "investment-report-publishing.wordpress.app-password=wp-password")
        .run(context -> assertThat(context).hasSingleBean(WordPressMediaClient.class));
  }

  @Test
  void contextStartsWithoutWordPressBeansWhenPublishingIsDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(WordPressMediaClient.class);
          assertThat(context).doesNotHaveBean(WordPressProperties.class);
        });
  }
}
