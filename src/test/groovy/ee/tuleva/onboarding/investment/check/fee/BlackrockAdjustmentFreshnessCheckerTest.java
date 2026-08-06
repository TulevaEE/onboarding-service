package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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
    checker = new BlackrockAdjustmentFreshnessChecker(navLedgerRepository, 5);
  }

  @Test
  void aRecentAdjustmentPasses() {
    givenLatestAdjustmentOn(LocalDate.of(2026, 6, 8));

    assertThat(checker.check(TUK75, CHECK_DATE).getFirst().severity()).isEqualTo(PASS);
  }

  @Test
  void anAdjustmentOlderThanTheMaximumAgeWarns() {
    givenLatestAdjustmentOn(LocalDate.of(2026, 6, 4));

    var finding = checker.check(TUK75, CHECK_DATE).getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.message()).contains("2026-06-04");
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
