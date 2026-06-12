package ee.tuleva.onboarding.investment.transaction.ingest;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.ingest.QuantityAmountMismatchEvent.MismatchKind;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
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
class QuantityAmountMismatchAlertListenerTest {

  @Mock private EmailService emailService;
  @Mock private OperationsNotificationService notificationService;

  private final AlertProperties alertProperties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));

  private QuantityAmountMismatchAlertListener listener() {
    return new QuantityAmountMismatchAlertListener(
        new AlertMandrillMessageFactory(alertProperties), emailService, notificationService);
  }

  @Test
  void onQuantityAmountMismatch_sendsSlackNotificationWithKeyDetails() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    listener().onQuantityAmountMismatch(sampleEvent());

    verify(notificationService)
        .sendMessage(
            """
            ⚠️ SEB tehingu koguse/summa lahknevus – 2026-05-13
            Order 123, ISIN: IE000F60HVH9, liik: ETF_QUANTITY
            Expected: 15007, actual: 15007.0003, delta: 0.0003""",
            INVESTMENT);
  }

  @Test
  void onQuantityAmountMismatch_sendsSystemEmailWithSubjectAndBody() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    QuantityAmountMismatchEvent event = sampleEvent();
    UUID clientRef = event.row().clientRef();

    listener().onQuantityAmountMismatch(event);

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    MandrillMessage message = captor.getValue();

    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Tuleva");
    assertThat(message.getSubject())
        .isEqualTo("[HOIATUS] Tehingute koguse/summa lahknevused – 2026-05-13");

    assertThat(message.getTo()).hasSize(2);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("taavi.pertman@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(CC);

    assertThat(message.getText())
        .contains("Tolerance kind: ETF_QUANTITY")
        .contains("Raporti kuupäev: 2026-05-13")
        .contains("Order id: 123")
        .contains("Expected: 15007")
        .contains("Actual: 15007.0003")
        .contains("Delta: 0.0003")
        .contains("ISIN: IE000F60HVH9")
        .contains("Client ref: " + clientRef)
        .contains("Our ref: DLA0799512")
        .contains("Side: BUY")
        .contains("Client name: Tuleva Täiendav Kogumisfond");
  }

  private static QuantityAmountMismatchEvent sampleEvent() {
    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    SebPendingTransactionRow row =
        new SebPendingTransactionRow(
            clientRef,
            "DLA0799512",
            "IE000F60HVH9",
            new BigDecimal("15007.0003"),
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
    TransactionOrder nearMiss =
        TransactionOrder.builder()
            .id(123L)
            .fund(TKF100)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal("15007"))
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .orderStatus(SENT)
            .build();

    return new QuantityAmountMismatchEvent(
        row,
        nearMiss,
        MismatchKind.ETF_QUANTITY,
        new BigDecimal("15007"),
        new BigDecimal("15007.0003"),
        new BigDecimal("0.0003"),
        reportDate);
  }
}
