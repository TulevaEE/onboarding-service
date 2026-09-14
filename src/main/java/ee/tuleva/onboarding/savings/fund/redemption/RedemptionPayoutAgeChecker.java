package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYOUT_OVERDUE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedemptionPayoutAgeChecker {
  static final List<RedemptionRequest.Status> NOT_YET_INITIATED =
      List.of(RESERVED, IN_REVIEW, VERIFIED, FAILED);

  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionPayoutDeadline deadline;
  private final PaymentCheckService paymentCheckService;

  void checkOverduePayouts(LocalDate today) {
    redemptionRequestRepository.findByStatusIn(NOT_YET_INITIATED).stream()
        .filter(request -> !today.isBefore(deadline.warnFrom(request.getRequestedAt())))
        .forEach(request -> report(request, today));
  }

  private void report(RedemptionRequest request, LocalDate today) {
    paymentCheckService.record(
        PAYOUT_OVERDUE,
        WARNING,
        "%s:%s".formatted(request.getId(), today),
        "ordered %s, still %s, and the payout must be initiated by %s"
            .formatted(
                deadline.orderDay(request.getRequestedAt()),
                request.getStatus(),
                deadline.bound(request.getRequestedAt())));
  }
}
