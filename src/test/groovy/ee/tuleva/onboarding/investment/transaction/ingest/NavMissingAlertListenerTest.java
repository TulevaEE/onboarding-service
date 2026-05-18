package ee.tuleva.onboarding.investment.transaction.ingest;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavMissingAlertListenerTest {

  @Mock private EmailService emailService;

  private final AlertProperties alertProperties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));

  private NavMissingAlertListener listener() {
    return new NavMissingAlertListener(
        new AlertMandrillMessageFactory(alertProperties), emailService);
  }

  @Test
  void onNavMissing_sendsInformationalAlert() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    NavMissingEvent event = new NavMissingEvent(42L, "IE000F60HVH9", LocalDate.of(2026, 5, 11));

    listener().onNavMissing(event);

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    MandrillMessage message = captor.getValue();

    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getSubject()).isEqualTo("[INFO] NAV andmed puuduvad – 2026-05-11");

    assertThat(message.getTo()).hasSize(2);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("taavi.pertman@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(CC);

    assertThat(message.getText())
        .contains("Execution id: 42")
        .contains("ISIN: IE000F60HVH9")
        .contains("Trade date: 2026-05-11");
  }
}
