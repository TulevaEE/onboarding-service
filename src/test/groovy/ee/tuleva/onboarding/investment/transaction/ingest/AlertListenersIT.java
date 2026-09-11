package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlertListenersIT {

  private final AlertMandrillMessageFactory messageFactory =
      new AlertMandrillMessageFactory(new AlertProperties(List.of("ops@tuleva.ee"), List.of()));

  @Test
  void executionMismatchEvent_isDispatchedToItsListener() {
    EmailService emailService = mock(EmailService.class);
    given(emailService.sendSystemEmail(any(MandrillMessage.class))).willReturn(true);
    OperationsNotificationService notificationService = mock(OperationsNotificationService.class);

    new ApplicationContextRunner()
        .withBean(
            ExecutionMismatchAlertListener.class, messageFactory, emailService, notificationService)
        .run(
            context -> {
              context.publishEvent(
                  new ExecutionMismatchEvent(
                      42L,
                      "IE000F60HVH9",
                      new BigDecimal("4.7255"),
                      new BigDecimal("4.7800"),
                      new BigDecimal("1.15"),
                      LocalDate.of(2026, 5, 11)));

              verify(emailService).sendSystemEmail(any(MandrillMessage.class));
            });
  }

  @Test
  void navMissingEvent_isDispatchedToItsListener() {
    EmailService emailService = mock(EmailService.class);
    given(emailService.sendSystemEmail(any(MandrillMessage.class))).willReturn(true);

    new ApplicationContextRunner()
        .withBean(NavMissingAlertListener.class, messageFactory, emailService)
        .run(
            context -> {
              context.publishEvent(
                  new NavMissingEvent(42L, "IE000F60HVH9", LocalDate.of(2026, 5, 11)));

              verify(emailService).sendSystemEmail(any(MandrillMessage.class));
            });
  }

  @Test
  void unrelatedEvent_reachesNeitherListener() {
    EmailService emailService = mock(EmailService.class);
    OperationsNotificationService notificationService = mock(OperationsNotificationService.class);

    new ApplicationContextRunner()
        .withBean(
            ExecutionMismatchAlertListener.class, messageFactory, emailService, notificationService)
        .withBean(NavMissingAlertListener.class, messageFactory, emailService)
        .run(
            context -> {
              context.publishEvent(new Object());

              verifyNoInteractions(emailService, notificationService);
            });
  }
}
