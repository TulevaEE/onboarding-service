package ee.tuleva.onboarding.investment.transaction.ingest;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnmatchedExecutionAlertListenerTest {

  @Mock private EmailService emailService;

  private final AlertProperties alertProperties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));

  private UnmatchedExecutionAlertListener listener() {
    return new UnmatchedExecutionAlertListener(
        new AlertMandrillMessageFactory(alertProperties), emailService);
  }

  @Test
  void onUnmatchedPendingTransaction_sendsSystemEmailWithSubjectAndBody() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    SebPendingTransactionRow row =
        new SebPendingTransactionRow(
            clientRef,
            "DLA0799512",
            "IE000F60HVH9",
            new BigDecimal("15007"),
            new BigDecimal("4.7255"),
            new BigDecimal("70915.58"),
            BigDecimal.ZERO,
            new BigDecimal("70915.58"),
            BUY,
            Instant.parse("2026-05-11T10:26:04Z"),
            LocalDate.of(2026, 5, 13),
            "Tuleva Täiendav Kogumisfond",
            "VP68168",
            "ICAV Amundi MSCI USA Screened UCITS ETF");

    listener().onUnmatchedPendingTransaction(new UnmatchedPendingTransactionEvent(row, reportDate));

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    MandrillMessage message = captor.getValue();

    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Tuleva");
    assertThat(message.getSubject())
        .isEqualTo("[HOIATUS] Matchimata tehing SEB raportis – 2026-05-13");

    assertThat(message.getTo()).hasSize(2);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("taavi.pertman@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(CC);

    assertThat(message.getText())
        .contains("Raporti kuupäev: 2026-05-13")
        .contains("ISIN: IE000F60HVH9")
        .contains("Client ref: " + clientRef)
        .contains("Our ref: DLA0799512")
        .contains("Quantity: 15007")
        .contains("Side: BUY")
        .contains("Trade date: 2026-05-11T10:26:04Z")
        .contains("Client name: Tuleva Täiendav Kogumisfond")
        .contains("Account: VP68168");
  }

  @Test
  void onUnmatchedPendingTransaction_missingClientRef_rendersPlaceholder() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    SebPendingTransactionRow row =
        new SebPendingTransactionRow(
            null,
            "DLA0000000",
            "IE000F60HVH9",
            new BigDecimal("100"),
            new BigDecimal("1.00"),
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            BUY,
            Instant.parse("2026-05-11T10:26:04Z"),
            LocalDate.of(2026, 5, 13),
            "Tuleva Täiendav Kogumisfond",
            "VP68168",
            "Some ETF");

    listener().onUnmatchedPendingTransaction(new UnmatchedPendingTransactionEvent(row, reportDate));

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    assertThat(captor.getValue().getText()).contains("Client ref: (missing)");
  }
}
