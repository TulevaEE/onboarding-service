package ee.tuleva.onboarding.payment.recurring;

import ee.tuleva.onboarding.analytics.ThirdPillarContribution;
import ee.tuleva.onboarding.payment.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

record LinkPayment(LocalDate paidOn, BigDecimal amount) {

  private static final int MAX_DAYS_UNTIL_THE_REGISTRY_REPORTS_A_PAYMENT = 7;
  private static final ZoneId ESTONIAN_TIME = ZoneId.of("Europe/Tallinn");

  static Instant earliestPaymentTimeReportedOnOrAfter(LocalDate reportedOn) {
    return reportedOn
        .minusDays(MAX_DAYS_UNTIL_THE_REGISTRY_REPORTS_A_PAYMENT)
        .atStartOfDay(ESTONIAN_TIME)
        .toInstant();
  }

  static LinkPayment from(Payment payment) {
    return new LinkPayment(
        LocalDate.ofInstant(payment.getCreatedTime(), ESTONIAN_TIME), payment.getAmount());
  }

  boolean explains(ThirdPillarContribution contribution) {
    var reportedOn = contribution.reportedOn();
    return contribution.amount().compareTo(amount) == 0
        && reportedOn.isAfter(paidOn)
        && !reportedOn.isAfter(paidOn.plusDays(MAX_DAYS_UNTIL_THE_REGISTRY_REPORTS_A_PAYMENT));
  }
}
