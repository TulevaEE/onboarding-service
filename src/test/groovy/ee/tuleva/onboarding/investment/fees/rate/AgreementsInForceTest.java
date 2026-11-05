package ee.tuleva.onboarding.investment.fees.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgreementsInForceTest {

  private static final String ISIN = "ZZ0000000001";
  private static final BigDecimal PUBLISHED = new BigDecimal("0.0020");
  private static final YearMonth APRIL = YearMonth.of(2026, 4);
  private static final YearMonth MAY = YearMonth.of(2026, 5);
  private static final AgreementPeriod OPEN_SINCE_NEW_YEAR =
      new AgreementPeriod(1L, ISIN, PUBLISHED, LocalDate.of(2026, 1, 1), null);
  private static final AgreementPeriod OPEN_SINCE_APRIL_15 =
      new AgreementPeriod(2L, ISIN, PUBLISHED, LocalDate.of(2026, 4, 15), null);

  @Test
  void bothAgreementsApplyInTheMonthTheNewerOneBegins() {
    var agreements = new AgreementsInForce(List.of(OPEN_SINCE_NEW_YEAR, OPEN_SINCE_APRIL_15));

    assertThat(agreements.appliedDuring(APRIL)).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void anOpenAgreementReplacedBeforeTheMonthBeganDoesNotApplyInIt() {
    var agreements = new AgreementsInForce(List.of(OPEN_SINCE_NEW_YEAR, OPEN_SINCE_APRIL_15));

    assertThat(agreements.appliedDuring(MAY)).containsExactly(2L);
  }

  @Test
  void anOlderAgreementAppliesAgainOnceTheNewerOneInsideItEnds() {
    var agreements =
        new AgreementsInForce(
            List.of(
                OPEN_SINCE_NEW_YEAR,
                new AgreementPeriod(
                    2L, ISIN, PUBLISHED, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))));

    assertThat(agreements.appliedDuring(APRIL)).containsExactly(2L);
    assertThat(agreements.appliedDuring(MAY)).containsExactly(1L);
  }

  @Test
  void afterSeveralAgreementsEndedTheOneThatEndedLastIsTheLastEnded() {
    var endedLast =
        new AgreementPeriod(
            1L, ISIN, PUBLISHED, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
    var agreements =
        new AgreementsInForce(
            List.of(
                endedLast,
                new AgreementPeriod(
                    2L, ISIN, PUBLISHED, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))));

    assertThat(agreements.lastEndedBefore(ISIN, MAY.atDay(1))).contains(endedLast);
  }
}
