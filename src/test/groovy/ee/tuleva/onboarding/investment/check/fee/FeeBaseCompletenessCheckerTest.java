package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeBaseValue;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeBaseCompletenessCheckerTest {

  private static final LocalDate WORKING_DAY = LocalDate.of(2026, 6, 3);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 6, 6);
  private static final BigDecimal NAV_TOTAL = new BigDecimal("1000000.00");

  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private FundNavQueryService fundNavQueryService;
  @Mock private NavLedgerRepository navLedgerRepository;

  private FeeBaseCompletenessChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new FeeBaseCompletenessChecker(
            feeAccrualRepository,
            fundNavQueryService,
            navLedgerRepository,
            new PublicHolidays(),
            new BigDecimal("0.01"));
  }

  @Test
  void aBaseMatchingTheNavComponentsPasses() {
    givenAccruals(base(WORKING_DAY, MANAGEMENT, NAV_TOTAL), base(WORKING_DAY, DEPOT, NAV_TOTAL));
    givenNavTotal(WORKING_DAY, NAV_TOTAL);

    assertThat(check(TUK75)).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  @Test
  void aBaseMissingBlackrockAndPendingRedemptionsFails() {
    var missing = new BigDecimal("44980.96");
    var buggyBase = NAV_TOTAL.subtract(missing);
    givenAccruals(base(WORKING_DAY, MANAGEMENT, buggyBase), base(WORKING_DAY, DEPOT, buggyBase));
    givenNavTotal(WORKING_DAY, NAV_TOTAL);

    var finding = check(TUK75).getFirst();

    assertThat(finding.severity()).isEqualTo(FAIL);
    assertThat(finding.deviationAmount()).isEqualByComparingTo(missing);
  }

  @Test
  void aSmallDifferenceWithinToleranceStillPasses() {
    givenAccruals(
        base(WORKING_DAY, MANAGEMENT, NAV_TOTAL.add(new BigDecimal("0.01"))),
        base(WORKING_DAY, DEPOT, NAV_TOTAL.add(new BigDecimal("0.01"))));
    givenNavTotal(WORKING_DAY, NAV_TOTAL);

    assertThat(check(TUK75).getFirst().severity()).isEqualTo(PASS);
  }

  @Test
  void managementAndDepotDisagreeingOnTheBaseFails() {
    givenAccruals(
        base(WORKING_DAY, MANAGEMENT, NAV_TOTAL),
        base(WORKING_DAY, DEPOT, NAV_TOTAL.subtract(new BigDecimal("100.00"))));

    var finding = check(TUK75).getFirst();

    assertThat(finding.severity()).isEqualTo(FAIL);
    assertThat(finding.message()).contains("MANAGEMENT", "DEPOT");
  }

  @Test
  void aWorkingDayWithNoNavReportRowsIsNotRunRatherThanADeviation() {
    givenAccruals(base(WORKING_DAY, MANAGEMENT, NAV_TOTAL));
    given(fundNavQueryService.findFeeBaseComponentTotal("TUK75", WORKING_DAY))
        .willReturn(Optional.empty());

    var finding = check(TUK75).getFirst();

    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.deviationAmount()).isNull();
  }

  @Test
  void gapFilledWeekendDaysAreSkippedEntirely() {
    givenAccruals(base(SATURDAY, MANAGEMENT, new BigDecimal("999.99")));

    assertThat(check(TUK75)).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  @Test
  void aSavingsFundAddsTheBlackrockLedgerBalanceWhichNavReportOmits() {
    var blackrock = new BigDecimal("56980.96");
    givenAccruals(
        base(WORKING_DAY, MANAGEMENT, NAV_TOTAL.add(blackrock)),
        base(WORKING_DAY, DEPOT, NAV_TOTAL.add(blackrock)));
    given(fundNavQueryService.findFeeBaseComponentTotal("TKF100", WORKING_DAY))
        .willReturn(Optional.of(NAV_TOTAL));
    given(navLedgerRepository.getSystemAccountBalanceBefore(any(), any())).willReturn(blackrock);

    assertThat(check(TKF100).getFirst().severity()).isEqualTo(PASS);
  }

  private List<FeeCheckFinding> check(TulevaFund fund) {
    return checker.check(fund, WORKING_DAY, SATURDAY);
  }

  private void givenAccruals(FeeBaseValue... values) {
    given(feeAccrualRepository.findBaseValuesBetween(any(), any(), any()))
        .willReturn(List.of(values));
  }

  private void givenNavTotal(LocalDate date, BigDecimal total) {
    given(fundNavQueryService.findFeeBaseComponentTotal("TUK75", date))
        .willReturn(Optional.of(total));
  }

  private FeeBaseValue base(LocalDate date, FeeType feeType, BigDecimal baseValue) {
    return new FeeBaseValue(date, feeType, baseValue);
  }
}
