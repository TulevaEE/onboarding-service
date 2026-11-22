package ee.tuleva.onboarding.investment.kid;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KidCalculationNotifierSelectionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(
              OperationsNotificationService.class,
              () -> Mockito.mock(OperationsNotificationService.class))
          .withBean(KidFunds.class, () -> KidFunds.TULEVA_FUNDS)
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(
              KidCalculationRepository.class, () -> Mockito.mock(KidCalculationRepository.class))
          .withBean(
              KidPrintedFiguresRepository.class,
              () -> Mockito.mock(KidPrintedFiguresRepository.class))
          .withUserConfiguration(
              SlackKidCalculationNotifier.class, LoggingKidCalculationNotifier.class);

  @Test
  void theDigestGoesToSlackWhenItIsEnabled() {
    contextRunner
        .withPropertyValues("investment.kid.digest.enabled=true")
        .run(
            context ->
                assertThat(context.getBean(KidCalculationNotifier.class))
                    .isInstanceOf(SlackKidCalculationNotifier.class));
  }

  @Test
  void theDigestIsOnlyLoggedWhenItIsOffOrNotConfigured() {
    contextRunner
        .withPropertyValues("investment.kid.digest.enabled=false")
        .run(
            context ->
                assertThat(context.getBean(KidCalculationNotifier.class))
                    .isInstanceOf(LoggingKidCalculationNotifier.class));
    contextRunner.run(
        context ->
            assertThat(context.getBean(KidCalculationNotifier.class))
                .isInstanceOf(LoggingKidCalculationNotifier.class));
  }
}
