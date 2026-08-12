package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.BLACKROCK_ADJUSTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeBaseValue;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeBaseCompletenessCheckerTest {

  private static final LocalDate EARLIER_WORKING_DAY = LocalDate.of(2026, 6, 2);
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

  // A fee type that was accruing and then stops charges the NAV only part of its fee. With no row
  // in the table and no entry in the ledger, nothing else on the daily path can see it: the ledger
  // check unions only the dates that exist on one side or the other.
  @Test
  void aFeeTypeThatStopsAccruingIsDetected() {
    givenAccruals(
        base(EARLIER_WORKING_DAY, MANAGEMENT, NAV_TOTAL),
        base(EARLIER_WORKING_DAY, DEPOT, NAV_TOTAL),
        base(WORKING_DAY, MANAGEMENT, NAV_TOTAL));
    given(fundNavQueryService.findFeeBaseComponentTotal("TUK75", EARLIER_WORKING_DAY))
        .willReturn(Optional.of(NAV_TOTAL));

    var finding = check(TUK75).getFirst();

    assertThat(finding.severity()).isEqualTo(FAIL);
    assertThat(finding.message()).contains("DEPOT");
  }

  // A fee type that is not in use yet legitimately has no rows at all. Every working day then has
  // one fee type and not the other, and none of them may be reported.
  @Test
  void aFeeTypeThatHasNotStartedAccruingYetRaisesNothing() {
    givenAccruals(
        base(EARLIER_WORKING_DAY, MANAGEMENT, NAV_TOTAL), base(WORKING_DAY, MANAGEMENT, NAV_TOTAL));
    givenNavTotal(WORKING_DAY, NAV_TOTAL);
    given(fundNavQueryService.findFeeBaseComponentTotal("TUK75", EARLIER_WORKING_DAY))
        .willReturn(Optional.of(NAV_TOTAL));

    assertThat(check(TUK75)).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  // The first day a fee type appears has nothing earlier to compare against, so the day depot
  // accrual starts must not read as the other fee type having stopped.
  @Test
  void theDayAFeeTypeStartsAccruingRaisesNothing() {
    givenAccruals(
        base(EARLIER_WORKING_DAY, MANAGEMENT, NAV_TOTAL),
        base(WORKING_DAY, MANAGEMENT, NAV_TOTAL),
        base(WORKING_DAY, DEPOT, NAV_TOTAL));
    givenNavTotal(WORKING_DAY, NAV_TOTAL);
    given(fundNavQueryService.findFeeBaseComponentTotal("TUK75", EARLIER_WORKING_DAY))
        .willReturn(Optional.of(NAV_TOTAL));

    assertThat(check(TUK75)).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
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

  // The NAV read the adjustment at its own cutoff on the calculation day, 15:20 for the savings
  // fund, and the accrual date is the position report date, the working day before. An adjustment
  // posted for the calculation day is stamped 08:00, so it is inside the window the NAV charged
  // against but outside a window that ends at midnight - and the checker headlined a correct fee
  // base as needing manual correction.
  @Test
  void theBlackrockAdjustmentIsReadAtTheCutoffTheNavChargedAt() {
    var blackrock = new BigDecimal("56980.96");
    givenAccruals(
        base(WORKING_DAY, MANAGEMENT, NAV_TOTAL.add(blackrock)),
        base(WORKING_DAY, DEPOT, NAV_TOTAL.add(blackrock)));
    given(fundNavQueryService.findFeeBaseComponentTotal("TKF100", WORKING_DAY))
        .willReturn(Optional.of(NAV_TOTAL));
    given(
            navLedgerRepository.getSystemAccountBalanceBefore(
                BLACKROCK_ADJUSTMENT.getAccountName(TKF100), navCutoffCharging(WORKING_DAY)))
        .willReturn(blackrock);

    assertThat(check(TKF100).getFirst().severity()).isEqualTo(PASS);
  }

  private static Instant navCutoffCharging(LocalDate positionReportDate) {
    return new PublicHolidays()
        .nextWorkingDay(positionReportDate)
        .atTime(TKF100.getNavCutoffTime())
        .atZone(ZoneId.of("Europe/Tallinn"))
        .toInstant();
  }

  // A month-long regression names every day it found, which would be an unreadable Slack message;
  // the tail is counted instead of listed.
  @Test
  void aLongRunOfWrongDaysListsTheFirstTenAndCountsTheRest() {
    var days = workingDays(LocalDate.of(2026, 6, 1), 12);
    var buggyBase = NAV_TOTAL.subtract(new BigDecimal("1000"));
    given(feeAccrualRepository.findBaseValuesBetween(any(), any(), any()))
        .willReturn(
            days.stream()
                .flatMap(
                    day -> Stream.of(base(day, MANAGEMENT, buggyBase), base(day, DEPOT, buggyBase)))
                .toList());
    given(fundNavQueryService.findFeeBaseComponentTotal(eq("TUK75"), any()))
        .willReturn(Optional.of(NAV_TOTAL));

    var finding = checker.check(TUK75, days.getFirst(), days.getLast()).getFirst();

    assertThat(finding.severity()).isEqualTo(FAIL);
    assertThat(finding.message()).contains(" ... (2 more)");
    assertThat(finding.deviationAmount()).isEqualByComparingTo(new BigDecimal("12000"));
  }

  private static List<LocalDate> workingDays(LocalDate from, int count) {
    var holidays = new PublicHolidays();
    var days = new ArrayList<LocalDate>(count);
    var day = from;
    while (days.size() < count) {
      if (holidays.isWorkingDay(day)) {
        days.add(day);
      }
      day = day.plusDays(1);
    }
    return days;
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
