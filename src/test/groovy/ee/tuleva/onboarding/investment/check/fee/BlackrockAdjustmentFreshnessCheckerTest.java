package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlackrockAdjustmentFreshnessCheckerTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 6, 10);

  @Mock private NavLedgerRepository navLedgerRepository;

  private BlackrockAdjustmentFreshnessChecker checker;

  @BeforeEach
  void setUp() {
    checker = new BlackrockAdjustmentFreshnessChecker(navLedgerRepository, new PublicHolidays(), 5);
  }

  @Test
  void aRecentAdjustmentPasses() {
    givenLatestAdjustmentOn(LocalDate.of(2026, 6, 8));

    assertThat(checker.check(TUK75, CHECK_DATE).getFirst().severity()).isEqualTo(PASS);
  }

  @Test
  void anAdjustmentOlderThanTheMaximumAgeWarns() {
    givenLatestAdjustmentOn(LocalDate.of(2026, 6, 2));

    var finding = checker.check(TUK75, CHECK_DATE).getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.message()).contains("2026-06-02");
  }

  // The adjustment is only ever posted on a business day, so its age has to be measured in business
  // days too. Over Estonian Christmas - the 24th, 25th and 26th are public holidays, and in 2025
  // they are followed straight by a weekend - the last working day before the break is six calendar
  // days but only one working day behind the first working day after it. Counted in calendar days
  // every fund warns, every year, with nothing missed and nothing for anyone to do.
  @Test
  void aChristmasBreakIsNotStaleness() {
    givenLatestAdjustmentOn(LocalDate.of(2025, 12, 23));

    assertThat(checker.check(TUK75, LocalDate.of(2025, 12, 29)).getFirst().severity())
        .isEqualTo(PASS);
  }

  @Test
  void aFundThatNeverHadAnAdjustmentIsNotRunRatherThanAWarning() {
    given(navLedgerRepository.findLatestTransactionDateByType(any(), any()))
        .willReturn(Optional.empty());

    assertThat(checker.check(TUK75, CHECK_DATE).getFirst().severity()).isEqualTo(NOT_RUN);
  }

  private void givenLatestAdjustmentOn(LocalDate date) {
    given(navLedgerRepository.findLatestTransactionDateByType(any(), any()))
        .willReturn(Optional.of(instantAt(date)));
  }

  private Instant instantAt(LocalDate date) {
    return date.atTime(8, 0).atZone(ZoneId.of("Europe/Tallinn")).toInstant();
  }
}
