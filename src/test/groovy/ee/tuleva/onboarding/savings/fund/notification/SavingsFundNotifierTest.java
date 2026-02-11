package ee.tuleva.onboarding.savings.fund.notification;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundNotifierTest {

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private SavingsFundNotifier notifier;

  @Test
  void onReservationCompleted_sendsNotification() {
    var event = new ReservationCompletedEvent(5, new BigDecimal("2500.00"));

    notifier.onReservationCompleted(event);

    verify(notificationService)
        .sendMessage("Savings fund reservation: payments=5, totalAmount=2500.00 EUR", SAVINGS);
  }

  @Test
  void onIssuingCompleted_sendsNotification() {
    var event =
        new IssuingCompletedEvent(
            3, new BigDecimal("1500.00"), new BigDecimal("150.12345"), new BigDecimal("9.9918"));

    notifier.onIssuingCompleted(event);

    verify(notificationService)
        .sendMessage(
            "Savings fund issuing: payments=3, totalAmount=1500.00 EUR, fundUnitsIssued=150.12345, NAV=9.9918",
            SAVINGS);
  }

  @Test
  void onSubscriptionBatchSent_sendsNotification() {
    var event = new SubscriptionBatchSentEvent(3, new BigDecimal("1500.00"));

    notifier.onSubscriptionBatchSent(event);

    verify(notificationService)
        .sendMessage(
            "Savings fund subscription batch sent to SEB: payments=3, totalAmount=1500.00 EUR",
            SAVINGS);
  }

  @Test
  void onRedemptionBatchCompleted_sendsNotification() {
    var event =
        new RedemptionBatchCompletedEvent(2, 2, new BigDecimal("500.00"), new BigDecimal("9.9918"));

    notifier.onRedemptionBatchCompleted(event);

    verify(notificationService)
        .sendMessage(
            "Savings fund redemption batch: requests=2, payouts=2, totalCashAmount=500.00 EUR, NAV=9.9918",
            SAVINGS);
  }

  @Test
  void onPaymentsReturned_sendsNotification() {
    var event = new PaymentsReturnedEvent(2);

    notifier.onPaymentsReturned(event);

    verify(notificationService).sendMessage("Savings fund returns: payments=2", SAVINGS);
  }
}
