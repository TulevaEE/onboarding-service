package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PossibleReportTruncationAlertListenerTest {

  @Mock private EmailService emailService;
  @Mock private OperationsNotificationService notificationService;

  private final AlertProperties alertProperties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));

  private PossibleReportTruncationAlertListener listener() {
    return new PossibleReportTruncationAlertListener(
        new AlertMandrillMessageFactory(alertProperties), emailService, notificationService);
  }

  private static PossibleReportTruncationEvent sampleEvent() {
    return new PossibleReportTruncationEvent(
        LocalDate.parse("2026-05-13"), 5, LocalDate.parse("2026-05-12"), 50);
  }

  @Test
  void sendsSlackNotificationWithKeyDetails() {
    given(emailService.sendSystemEmail(any(MandrillMessage.class))).willReturn(true);

    listener().onPossibleReportTruncation(sampleEvent());

    verify(notificationService)
        .sendMessage(
            """
            ⚠️ SEB pending transactions raport võib olla poolik – 2026-05-13
            Ridu: 5 (eelmine raport 2026-05-12: 50 rida)
            Kadumise põhjal tuvastamist ei tehtud, oodatakse käsitsi kontrolli.""",
            INVESTMENT);
  }

  @Test
  void sendsEmailContainingRowCounts() {
    given(emailService.sendSystemEmail(any(MandrillMessage.class))).willReturn(true);

    listener().onPossibleReportTruncation(sampleEvent());

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    MandrillMessage message = captor.getValue();
    assertThat(message.getSubject())
        .isEqualTo("[HOIATUS] SEB pending transactions raport võib olla poolik – 2026-05-13");
    assertThat(message.getText()).contains("Raporti kuupäev: 2026-05-13");
    assertThat(message.getText()).contains("Ridu: 5");
    assertThat(message.getText()).contains("Eelmise raporti kuupäev: 2026-05-12");
    assertThat(message.getText()).contains("Eelmise raporti ridu: 50");
  }
}
