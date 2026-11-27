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
            🟠 FT email delivery delayed: desk@example.com
            "Tuleva ETF orders 26.11.2026 – MOC" · deferral: 450 try later (id=msg_1)
            What to do: Mandrill keeps trying. If the desk hasn't confirmed the orders before \
            the cut-off, call the desk. Send by hand only if the desk agrees to leave the late \
            original unexecuted.""");
    assertThat(OrderEmailDeliveryText.deliveryFailureSeverity(event)).isEqualTo(WARNING);
  }

  @Test
  void aDelayedUuidEmail_asksForNothingYet() {
    var event = deliveryFailure("deferral", "trade-order-uuid-workbook", null);

    assertThat(OrderEmailDeliveryText.deliveryFailure(event))
        .isEqualTo(
            """
            🟠 SEB UUID email delivery delayed: desk@example.com
            deferral: 450 try later (id=msg_1)
            What to do: nothing yet, Mandrill keeps trying. If the email bounces, another \
            message follows.""");
  }

  @Test
  void aBouncedOrderEmail_isRed_andAllowsAResendOnlyIfTheDeskConfirmsTheOrdersDidNotArrive() {
    var event = deliveryFailure("hard_bounce", "trade-order-seb-fund", null);

    assertThat(OrderEmailDeliveryText.deliveryFailure(event))
        .isEqualTo(
            """
            🔴 SEB index fund email NOT delivered: desk@example.com
            hard_bounce: 450 try later (id=msg_1)
            What to do: if this address is a copy, don't send the orders again. Otherwise call \
            the desk and send the file by hand only if the desk confirms the orders did not \
            arrive.""");
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
            🟠 SEB UUID email NOT delivered: desk@example.com
            "Tehingukorralduste UUID 26.11.2026" · reject: 450 try later (id=msg_1)
            What to do: send the UUID file to SEB by hand. The orders go in emails of their own.""");
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
        .startsWith("🔴 Order email NOT delivered: desk@example.com\n");
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
