package ee.tuleva.onboarding.investment.transaction.ingest;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionMismatchAlertListenerTest {

  @Mock private EmailService emailService;
  @Mock private OperationsNotificationService notificationService;

  private final AlertProperties alertProperties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));

  private ExecutionMismatchAlertListener listener() {
    return new ExecutionMismatchAlertListener(
        new AlertMandrillMessageFactory(alertProperties), emailService, notificationService);
  }

  @Test
  void onExecutionMismatch_sendsSlackNotificationWithKeyDetails() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    listener().onExecutionMismatch(sampleEvent());

    verify(notificationService)
        .sendMessage(
            """
            ⚠️ SEB hind erineb NAV-hinnast – 2026-05-11
            Execution 42, ISIN: IE000F60HVH9
            Exec hind: 4.7255, NAV hind: 4.7475, delta %: -0.46""",
            INVESTMENT);
  }

  @Test
  void onExecutionMismatch_sendsAlertWithPriceComparison() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    listener().onExecutionMismatch(sampleEvent());

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    MandrillMessage message = captor.getValue();

    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getSubject())
        .isEqualTo("[HOIATUS] SEB hind erineb NAV-hinnast – 2026-05-11");

    assertThat(message.getTo()).hasSize(2);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("taavi.pertman@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(CC);

    assertThat(message.getText())
        .contains("Execution id: 42")
        .contains("ISIN: IE000F60HVH9")
        .contains("Exec price: 4.7255")
        .contains("NAV price: 4.7475")
        .contains("Delta %: -0.46")
        .contains("Trade date: 2026-05-11");
  }

  private static ExecutionMismatchEvent sampleEvent() {
    return new ExecutionMismatchEvent(
        42L,
        "IE000F60HVH9",
        new BigDecimal("4.7255"),
        new BigDecimal("4.7475"),
        new BigDecimal("-0.46"),
        LocalDate.of(2026, 5, 11));
  }
}
