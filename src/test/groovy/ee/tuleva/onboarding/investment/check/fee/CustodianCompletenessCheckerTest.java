package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
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
class CustodianCompletenessCheckerTest {

  private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
  private static final LocalDate TO = LocalDate.of(2026, 6, 4);
  private static final LocalDate EARLIER_POSITION_DATE = LocalDate.of(2026, 5, 20);
  private static final LocalDate POSITION_DATE = LocalDate.of(2026, 6, 3);
  private static final BigDecimal RECOGNISED = new BigDecimal("1646485.00");

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private FundNavQueryService fundNavQueryService;

  private CustodianCompletenessChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new CustodianCompletenessChecker(
            fundPositionRepository, fundNavQueryService, new BigDecimal("1.00"));
  }

  @Test
  void aCustodianTotalMatchingTheRecognisedTotalPasses() {
    givenPositionDates(POSITION_DATE);
    givenRecognised(POSITION_DATE, RECOGNISED);
    givenCustodian(POSITION_DATE, RECOGNISED);

    assertThat(check()).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  @Test
  void aLiabilityRowTheLedgerNeverRecognisedIsReported() {
    var unrecognisedLiability = new BigDecimal("-12500.00");
    givenPositionDates(POSITION_DATE);
    givenRecognised(POSITION_DATE, RECOGNISED);
    givenCustodian(POSITION_DATE, RECOGNISED.add(unrecognisedLiability));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.deviationAmount()).isEqualByComparingTo(unrecognisedLiability.abs());
    assertThat(finding.message()).contains(POSITION_DATE.toString());
  }

  @Test
  void aDifferenceWithinToleranceStillPasses() {
    givenPositionDates(POSITION_DATE);
    givenRecognised(POSITION_DATE, RECOGNISED);
    givenCustodian(POSITION_DATE, RECOGNISED.add(new BigDecimal("1.00")));

    assertThat(check().getFirst().severity()).isEqualTo(PASS);
  }

  @Test
  void noPositionReportInTheWindowIsNotRunRatherThanADeviation() {
    givenPositionDates();

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.deviationAmount()).isNull();
  }

  @Test
  void aPositionDateWithoutANavReportIsNotRun() {
    givenPositionDates(POSITION_DATE);
    given(fundNavQueryService.findCustodianComparableTotal("TUK75", POSITION_DATE))
        .willReturn(Optional.empty());

    assertThat(check().getFirst().severity()).isEqualTo(NOT_RUN);
  }

  // The window is 35 days wide, so only comparing the single latest position date in it meant a
  // wrong custodian input on any earlier day was never looked at - not on the day it landed if the
  // pipeline was behind, and never again afterwards.
  @Test
  void anUnrecognisedRowOnAnEarlierDayInTheWindowIsStillReported() {
    var unrecognised = new BigDecimal("-12500.00");
    givenPositionDates(EARLIER_POSITION_DATE, POSITION_DATE);
    givenRecognised(EARLIER_POSITION_DATE, RECOGNISED);
    givenCustodian(EARLIER_POSITION_DATE, RECOGNISED.add(unrecognised));
    givenRecognised(POSITION_DATE, RECOGNISED);
    givenCustodian(POSITION_DATE, RECOGNISED);

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.deviationAmount()).isEqualByComparingTo(unrecognised.abs());
    assertThat(finding.message()).contains(EARLIER_POSITION_DATE.toString());
  }

  @Test
  void everyMismatchingDayInTheWindowIsCounted() {
    var unrecognised = new BigDecimal("-12500.00");
    givenPositionDates(EARLIER_POSITION_DATE, POSITION_DATE);
    givenRecognised(EARLIER_POSITION_DATE, RECOGNISED);
    givenCustodian(EARLIER_POSITION_DATE, RECOGNISED.add(unrecognised));
    givenRecognised(POSITION_DATE, RECOGNISED);
    givenCustodian(POSITION_DATE, RECOGNISED.add(unrecognised));

    var finding = check().getFirst();

    assertThat(finding.deviationAmount())
        .isEqualByComparingTo(unrecognised.abs().multiply(new BigDecimal("2")));
    assertThat(finding.message()).contains(EARLIER_POSITION_DATE.toString(), "2 day(s)");
  }

  // A day with no nav_report is a gap in coverage, not a deviation, but it must not hide a real
  // mismatch found on another day in the same window.
  @Test
  void aMismatchOutranksADayThatCouldNotBeCompared() {
    givenPositionDates(EARLIER_POSITION_DATE, POSITION_DATE);
    given(fundNavQueryService.findCustodianComparableTotal("TUK75", EARLIER_POSITION_DATE))
        .willReturn(Optional.empty());
    givenRecognised(POSITION_DATE, RECOGNISED);
    givenCustodian(POSITION_DATE, RECOGNISED.add(new BigDecimal("-12500.00")));

    assertThat(check().getFirst().severity()).isEqualTo(WARNING);
  }

  private List<FeeCheckFinding> check() {
    return checker.check(TUK75, FROM, TO);
  }

  private void givenPositionDates(LocalDate... dates) {
    given(fundPositionRepository.findDistinctNavDatesByFundBetween(TUK75, FROM, TO))
        .willReturn(List.of(dates));
  }

  private void givenCustodian(LocalDate date, BigDecimal total) {
    given(fundPositionRepository.sumCustodianMarketValue(eq(TUK75), eq(date), any(), any()))
        .willReturn(total);
  }

  private void givenRecognised(LocalDate date, BigDecimal total) {
    given(fundNavQueryService.findCustodianComparableTotal("TUK75", date))
        .willReturn(Optional.of(total));
  }
}
