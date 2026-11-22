package ee.tuleva.onboarding.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ContactChangePropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ContactChangeConfiguration.class);

  @Test
  void theApplicationConfigurationKeepsAReplacedEmailNotifiableForFourWeeks() {
    contextRunner
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .run(
            context ->
                assertThat(
                        context.getBean(ContactChangeProperties.class).replacedEmailNoticeWindow())
                    .isEqualTo(Duration.ofDays(28)));
  }

  @Test
  void theWindowIsReadFromItsProperty() {
    contextRunner
        .withPropertyValues("contact-change.replaced-email-notice-window=7d")
        .run(
            context ->
                assertThat(
                        context.getBean(ContactChangeProperties.class).replacedEmailNoticeWindow())
                    .isEqualTo(Duration.ofDays(7)));
  }

  @Test
  void aMissingWindowFailsAtStartupRatherThanAtTheFirstNotice() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void aWindowThatIsNotPositiveFailsAtStartupRatherThanNotifyingNobody() {
    contextRunner
        .withPropertyValues("contact-change.replaced-email-notice-window=0d")
        .run(context -> assertThat(context).hasFailed());
  }
}
