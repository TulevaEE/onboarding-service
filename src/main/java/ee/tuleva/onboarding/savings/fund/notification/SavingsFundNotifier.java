package ee.tuleva.onboarding.savings.fund.notification;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SavingsFundNotifier {

  private final OperationsNotificationService notificationService;

  @EventListener
  public void onReservationCompleted(ReservationCompletedEvent event) {
    try {
      notificationService.sendMessage(
          "Savings fund reservation: payments=%d, totalAmount=%s EUR"
              .formatted(event.paymentCount(), event.totalAmount()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send reservation notification", e);
    }
  }

  @EventListener
  public void onIssuingCompleted(IssuingCompletedEvent event) {
    try {
      notificationService.sendMessage(
          "Savings fund issuing: payments=%d, totalAmount=%s EUR, fundUnitsIssued=%s, NAV=%s"
              .formatted(
                  event.paymentCount(), event.totalAmount(), event.totalFundUnits(), event.nav()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send issuing notification", e);
    }
  }

  @EventListener
  public void onSubscriptionBatchSent(SubscriptionBatchSentEvent event) {
    try {
      notificationService.sendMessage(
          "Savings fund subscription batch sent to SEB: totalAmount=%s EUR"
              .formatted(event.totalAmount()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send subscription batch notification", e);
    }
  }

  @EventListener
  public void onRedemptionBatchCompleted(RedemptionBatchCompletedEvent event) {
    try {
      notificationService.sendMessage(
          "Savings fund redemption batch: requests=%d, payouts=%d, totalCashAmount=%s EUR, NAV=%s"
              .formatted(
                  event.requestCount(), event.payoutCount(), event.totalCashAmount(), event.nav()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send redemption batch notification", e);
    }
  }

  @EventListener
  public void onPaymentsReturned(PaymentsReturnedEvent event) {
    try {
      notificationService.sendMessage(
          "Savings fund returns: payments=%d, totalAmount=%s EUR"
              .formatted(event.paymentCount(), event.totalAmount()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send payments returned notification", e);
    }
  }

  @EventListener
  public void onDeferredReturnMatchingCompleted(DeferredReturnMatchingCompletedEvent event) {
    try {
      notificationService.sendMessage(
          "Deferred return matching: matchedCount=%d, totalAmount=%s EUR"
              .formatted(event.matchedCount(), event.totalAmount()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send deferred return matching notification", e);
    }
  }

  @EventListener
  public void onRedemptionRequested(RedemptionRequestedEvent event) {
    try {
      notificationService.sendMessage(
          "Savings fund redemption requested: requestedAmount=%s EUR, fundUnits=%s, redemptionRequestId=%s"
              .formatted(
                  event.requestedAmount(), event.fundUnits(), event.redemptionRequestId()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send redemption requested notification", e);
    }
  }

  @EventListener
  public void onUnattributedPayment(UnattributedPaymentEvent event) {
    try {
      notificationService.sendMessage(
          "Savings fund unattributed payment: amount=%s EUR, reason=%s, paymentId=%s"
              .formatted(event.amount(), event.returnReason(), event.paymentId()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send unattributed payment notification", e);
    }
  }

  @EventListener
  public void onTrusteeReportSent(TrusteeReportSentEvent event) {
    try {
      notificationService.sendMessage(
          "Savings fund trustee report sent: date=%s, rows=%d, NAV=%s, issuedUnits=%s, issuedAmount=%s, redeemedUnits=%s, redeemedAmount=%s, totalOutstandingUnits=%s"
              .formatted(
                  event.reportDate(),
                  event.rowCount(),
                  event.nav(),
                  event.issuedUnits(),
                  event.issuedAmount(),
                  event.redeemedUnits(),
                  event.redeemedAmount(),
                  event.totalOutstandingUnits()),
          SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send trustee report notification", e);
    }
  }
}
