package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OrderEmailDeliveryTextTest {

  @Test
  void aDelayedOrderEmail_isAmber_andAllowsAResendOnlyIfTheDeskAgreesToLeaveTheOriginal() {
    var event =
        deliveryFailure("deferral", "trade-order-ft-etf", "Tuleva ETF orders 26.11.2026 – MOC");

    assertThat(OrderEmailDeliveryText.deliveryFailure(event))
        .isEqualTo(
            """
            🟠 FT kirja kohaletoimetamine viibib: desk@example.com
            „Tuleva ETF orders 26.11.2026 – MOC“ · deferral: 450 try later (id=msg_1)
            Mida teha: Mandrill proovib ise uuesti. Kui laud pole korraldusi enne lõikeaega \
            kinnitanud, helista lauale. Saada käsitsi ainult siis, kui lepid lauaga kokku, \
            et hilinev originaal jäetakse täitmata.""");
    assertThat(OrderEmailDeliveryText.deliveryFailureSeverity(event)).isEqualTo(WARNING);
  }

  @Test
  void aDelayedUuidEmail_asksForNothingYet() {
    var event = deliveryFailure("deferral", "trade-order-uuid-workbook", null);

    assertThat(OrderEmailDeliveryText.deliveryFailure(event))
        .isEqualTo(
            """
            🟠 SEB UUID-kirja kohaletoimetamine viibib: desk@example.com
            deferral: 450 try later (id=msg_1)
            Mida teha: praegu mitte midagi, Mandrill proovib ise uuesti. \
            Kui kiri põrkab tagasi, tuleb uus teade.""");
  }

  @Test
  void aBouncedOrderEmail_isRed_andAllowsAResendOnlyIfTheDeskConfirmsTheOrdersDidNotArrive() {
    var event = deliveryFailure("hard_bounce", "trade-order-seb-fund", null);

    assertThat(OrderEmailDeliveryText.deliveryFailure(event))
        .isEqualTo(
            """
            🔴 SEB indeksfondide kiri ei jõudnud kohale: desk@example.com
            hard_bounce: 450 try later (id=msg_1)
            Mida teha: kui see aadress on koopia, korraldusi uuesti ei saada. Muidu helista \
            lauale ja saada fail käsitsi ainult siis, kui laud kinnitab, et korraldused ei jõudnud.""");
    assertThat(OrderEmailDeliveryText.deliveryFailureSeverity(event)).isEqualTo(ERROR);
  }

  @Test
  void aBouncedUuidEmail_isAmber() {
    var event =
        deliveryFailure(
            "reject", "trade-order-uuid-workbook", "Tehingukorralduste UUID 26.11.2026");

    assertThat(OrderEmailDeliveryText.deliveryFailure(event))
        .isEqualTo(
            """
            🟠 SEB UUID-kiri ei jõudnud kohale: desk@example.com
            „Tehingukorralduste UUID 26.11.2026“ · reject: 450 try later (id=msg_1)
            Mida teha: saada UUID-fail SEB-le käsitsi. Tehingukorraldused on eraldi kirjades.""");
    assertThat(OrderEmailDeliveryText.deliveryFailureSeverity(event)).isEqualTo(WARNING);
  }

  @Test
  void aBouncedEmailWithoutItsKindTag_isTreatedAsAnOrderEmail() {
    var event =
        new EmailDeliveryFailedEvent(
            "desk@example.com",
            "Orders",
            "soft_bounce",
            "450 try later",
            "msg_1",
            Instant.parse("2026-11-26T09:00:00Z"),
            List.of("trade-order"));

    assertThat(OrderEmailDeliveryText.deliveryFailure(event))
        .startsWith("🔴 Tehingukorralduse kiri ei jõudnud kohale: desk@example.com\n");
    assertThat(OrderEmailDeliveryText.deliveryFailureSeverity(event)).isEqualTo(ERROR);
  }

  private static EmailDeliveryFailedEvent deliveryFailure(
      String eventType, String kindTag, @Nullable String subject) {
    return new EmailDeliveryFailedEvent(
        "desk@example.com",
        subject,
        eventType,
        "450 try later",
        "msg_1",
        Instant.parse("2026-11-26T09:00:00Z"),
        List.of("trade-order", kindTag));
  }
}
