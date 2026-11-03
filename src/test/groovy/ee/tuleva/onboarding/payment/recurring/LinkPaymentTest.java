package ee.tuleva.onboarding.payment.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.analytics.ThirdPillarContribution;
import ee.tuleva.onboarding.payment.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkPaymentTest {

  private static final LocalDate PAID_ON = LocalDate.of(2026, 9, 30);
  private static final LinkPayment PAYMENT = new LinkPayment(PAID_ON, new BigDecimal("100.00"));

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 7})
  void explainsAContributionOfTheSameAmountReportedWithinAWeekAfterThePayment(int daysLater) {
    var contribution =
        new ThirdPillarContribution(PAID_ON.plusDays(daysLater), new BigDecimal("100.00"));

    assertThat(PAYMENT.explains(contribution)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 8})
  void doesNotExplainAContributionReportedBeforeTheNextDayOrMoreThanAWeekLater(int daysLater) {
    var contribution =
        new ThirdPillarContribution(PAID_ON.plusDays(daysLater), new BigDecimal("100.00"));

    assertThat(PAYMENT.explains(contribution)).isFalse();
  }

  @Test
  void doesNotExplainAContributionOfAnotherAmount() {
    var contribution = new ThirdPillarContribution(PAID_ON.plusDays(1), new BigDecimal("100.01"));

    assertThat(PAYMENT.explains(contribution)).isFalse();
  }

  @Test
  void comparesAmountsRegardlessOfScale() {
    var contribution = new ThirdPillarContribution(PAID_ON.plusDays(1), new BigDecimal("100"));

    assertThat(PAYMENT.explains(contribution)).isTrue();
  }

  @Test
  void looksForPaymentsFromAWeekBeforeTheFirstReportingDateInEstonianTime() {
    assertThat(LinkPayment.earliestPaymentTimeReportedOnOrAfter(LocalDate.of(2026, 7, 1)))
        .isEqualTo(Instant.parse("2026-06-23T21:00:00Z"));
  }

  @Test
  void datesThePaymentInEstonianTime() {
    var payment =
        Payment.builder()
            .createdTime(Instant.parse("2026-09-30T21:30:00Z"))
            .amount(new BigDecimal("100.00"))
            .build();

    assertThat(LinkPayment.from(payment))
        .isEqualTo(new LinkPayment(LocalDate.of(2026, 10, 1), new BigDecimal("100.00")));
  }
}
