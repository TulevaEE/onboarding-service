package ee.tuleva.onboarding.savings.fund.notification;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SavingsFundNotifierTest {

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private SavingsFundNotifier notifier;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(notifier, "mentionUserIds", List.of("U09C3B5B83D"));
  }

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
            "Savings fund subscription batch sent to SEB: totalAmount=1500.00 EUR", SAVINGS);
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
    var event = new PaymentsReturnedEvent(2, new BigDecimal("200.00"));

    notifier.onPaymentsReturned(event);

    verify(notificationService)
        .sendMessage("Savings fund returns: payments=2, totalAmount=200.00 EUR", SAVINGS);
  }

  @Test
  void onTrusteeReportSent_sendsNotification() {
    var event =
        new TrusteeReportSentEvent(
            LocalDate.of(2026, 2, 11),
            42,
            new BigDecimal("9.9918"),
            new BigDecimal("10.00000"),
            new BigDecimal("100.00"),
            new BigDecimal("5.00000"),
            new BigDecimal("50.00"),
            new BigDecimal("1005.00000"));

    notifier.onTrusteeReportSent(event);

    verify(notificationService)
        .sendMessage(
            "Savings fund trustee report sent: date=2026-02-11, rows=42, NAV=9.9918, issuedUnits=10.00000, issuedAmount=100.00, redeemedUnits=5.00000, redeemedAmount=50.00, totalOutstandingUnits=1005.00000",
            SAVINGS);
  }

  @Test
  void onRedemptionRequested_sendsNotification() {
    var requestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    var event =
        new RedemptionRequestedEvent(requestId, 42L, new BigDecimal("500.00"), new BigDecimal("50.12345"));

    notifier.onRedemptionRequested(event);

    verify(notificationService)
        .sendMessage(
            "<@U09C3B5B83D> Tegemisel on väljamakse: 500.00 EUR (50.12345 osakut). Lunastuse ID: 11111111-1111-1111-1111-111111111111",
            SAVINGS);
  }

  @Test
  void onUnattributedPayment_sendsNotification() {
    var paymentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    var event =
        new UnattributedPaymentEvent(paymentId, new BigDecimal("100.00"), "selgituses puudub isikukood");

    notifier.onUnattributedPayment(event);

    verify(notificationService)
        .sendMessage(
            "<@U09C3B5B83D> Kolmas isik kandis fondi kontole raha: 100.00 EUR. Põhjus: selgituses puudub isikukood. Makse ID: 22222222-2222-2222-2222-222222222222",
            SAVINGS);
  }

  @Test
  void onDeferredReturnMatchingCompleted_sendsNotification() {
    var event = new DeferredReturnMatchingCompletedEvent(2, 1, new BigDecimal("125.00"));

    notifier.onDeferredReturnMatchingCompleted(event);

    verify(notificationService)
        .sendMessage("Deferred return matching: matchedCount=2, totalAmount=125.00 EUR", SAVINGS);
  }
}
