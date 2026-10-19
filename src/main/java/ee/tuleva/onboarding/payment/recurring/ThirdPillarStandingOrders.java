package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.GIFT;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE;

import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.analytics.ThirdPillarContribution;
import ee.tuleva.onboarding.payment.PaymentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ThirdPillarStandingOrders {

  private final ThirdPillarAnalytics thirdPillarAnalytics;
  private final PaymentRepository paymentRepository;
  private final Clock clock;

  boolean paysByStandingOrder(String personalCode) {
    final int LOOKBACK_MONTHS = 4;
    final int MIN_STANDING_ORDER_MONTHS = 3;
    var windowStart = LocalDate.now(clock).withDayOfMonth(1).minusMonths(LOOKBACK_MONTHS - 1);
    var linkPayments = linkPaymentsReportedOnOrAfter(personalCode, windowStart);
    var standingOrderMonths =
        thirdPillarAnalytics.ownContributionsSince(personalCode, windowStart).stream()
            .filter(contribution -> !paidThroughLink(contribution, linkPayments))
            .map(contribution -> YearMonth.from(contribution.reportedOn()))
            .distinct()
            .count();
    return standingOrderMonths >= MIN_STANDING_ORDER_MONTHS;
  }

  private List<LinkPayment> linkPaymentsReportedOnOrAfter(String personalCode, LocalDate date) {
    return paymentRepository
        .findAllByRecipientPersonalCodeAndPaymentTypeInAndCreatedTimeGreaterThanEqual(
            personalCode,
            List.of(SINGLE, GIFT),
            LinkPayment.earliestPaymentTimeReportedOnOrAfter(date))
        .stream()
        .map(LinkPayment::from)
        .toList();
  }

  private static boolean paidThroughLink(
      ThirdPillarContribution contribution, List<LinkPayment> linkPayments) {
    return linkPayments.stream().anyMatch(payment -> payment.explains(contribution));
  }
}
