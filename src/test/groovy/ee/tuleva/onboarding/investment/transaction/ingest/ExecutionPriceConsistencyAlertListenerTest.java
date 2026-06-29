package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
class ExecutionPriceConsistencyAlertListenerTest {

  @Mock private EmailService emailService;
  @Mock private OperationsNotificationService notificationService;

  private final AlertProperties alertProperties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));

  private ExecutionPriceConsistencyAlertListener listener() {
    return new ExecutionPriceConsistencyAlertListener(
        new AlertMandrillMessageFactory(alertProperties), emailService, notificationService);
  }

  private static ExecutionPriceConsistencyEvent sampleEvent() {
    return new ExecutionPriceConsistencyEvent(
        42L,
        "IE000I9HGDZ3",
        new BigDecimal("9.99"),
        new BigDecimal("10.40"),
        new BigDecimal("0.041"),
        new BigDecimal("0.01"),
        LocalDate.parse("2026-06-29"));
  }

  @Test
  void sendsSlackNotificationWithKeyDetails() {
    given(emailService.sendSystemEmail(any(MandrillMessage.class))).willReturn(true);

    listener().onExecutionPriceConsistency(sampleEvent());

    verify(notificationService)
        .sendMessage(
            """
            ⚠️ SEB tehingu tükkide hinnad lahknevad – 2026-06-29
            Order 42, ISIN: IE000I9HGDZ3
            Min hind: 9.99, max hind: 10.40, hajuvus: 0.041 (lubatud 0.01)""",
            INVESTMENT);
  }

  @Test
  void sendsEmailContainingPriceSpread() {
    given(emailService.sendSystemEmail(any(MandrillMessage.class))).willReturn(true);

    listener().onExecutionPriceConsistency(sampleEvent());

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    MandrillMessage message = captor.getValue();
    assertThat(message.getSubject())
        .isEqualTo("[HOIATUS] Tehingu tükkide hinnad lahknevad – 2026-06-29");
    assertThat(message.getText()).contains("Order id: 42");
    assertThat(message.getText()).contains("ISIN: IE000I9HGDZ3");
    assertThat(message.getText()).contains("Min ühikuhind: 9.99");
    assertThat(message.getText()).contains("Max ühikuhind: 10.40");
  }
}
