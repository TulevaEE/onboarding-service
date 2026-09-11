package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.INFO;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
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
  private static final String RECEIVABLES = "Total receivables of unsettled transactions";
  private static final String PAYABLES = "Total payables of unsettled transactions";

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private CustodianPositionComparator comparator;

  private CustodianCompletenessChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new CustodianCompletenessChecker(
            fundPositionRepository, comparator, new BigDecimal("1.00"));
  }

  @Test
  void aReportMatchingTheNavPasses() {
    givenPositionDates(POSITION_DATE);
    givenComparison(matching(POSITION_DATE));

    assertThat(check()).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  @Test
  void aLineTheNavNeverRecognisedIsReportedWithBothSidesOfIt() {
    givenPositionDates(POSITION_DATE);
    givenComparison(differing(POSITION_DATE, difference(RECEIVABLES, "325708788.10", "0.00")));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.deviationAmount()).isEqualByComparingTo("325708788.10");
    assertThat(finding.message())
        .contains(
            POSITION_DATE.toString(),
            RECEIVABLES,
            "SEB report 325708788.10",
            "our NAV 0.00",
            "difference 325708788.10");
  }

  // The message has to name the account line, because the netted total it used to report reads as
  // one huge unexplained number even when a single line moved.
  @Test
  void everyDifferingLineIsNamedSeparately() {
    givenPositionDates(POSITION_DATE);
    givenComparison(
        differing(
            POSITION_DATE,
            difference(RECEIVABLES, "325708788.10", "0.00"),
            difference(PAYABLES, "-320994863.36", "0.00")));

    assertThat(check().getFirst().message()).contains(RECEIVABLES, PAYABLES);
  }

  // SEB sends the report, we calculate the NAV, and a trade confirmation lands afterwards. The NAV
  // was right on the evidence it had, so this must not read as something to correct.
  @Test
  void aReportReSentAfterTheNavWasCalculatedIsANoteRatherThanAWarning() {
    givenPositionDates(POSITION_DATE);
    givenComparison(
        lateCorrection(
            POSITION_DATE,
            new BigDecimal("-75247.49"),
            new BigDecimal("-0.67"),
            difference(RECEIVABLES, "325708788.10", "0.00")));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(INFO);
    assertThat(finding.message())
        .contains("re-sent", "No NAV correction is due", "-75247.49 EUR", "-0.67 bp");
  }

  // A stuck note must never mask a real mismatch: the notifier only speaks on a severity change, so
  // a genuine difference found on another day has to lift the whole finding back to WARNING.
  @Test
  void aGenuineMismatchOutranksALateCorrectionInTheSameWindow() {
    givenPositionDates(EARLIER_POSITION_DATE, POSITION_DATE);
    givenComparison(
        lateCorrection(
            EARLIER_POSITION_DATE,
            new BigDecimal("-75247.49"),
            new BigDecimal("-0.67"),
            difference(RECEIVABLES, "325708788.10", "0.00")));
    givenComparison(differing(POSITION_DATE, difference(PAYABLES, "-12500.00", "0.00")));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.message()).contains(POSITION_DATE.toString());
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
    given(comparator.compare(TUK75, POSITION_DATE)).willReturn(Optional.empty());

    assertThat(check().getFirst().severity()).isEqualTo(NOT_RUN);
  }

  // The window is 35 days wide, so only comparing the single latest position date in it meant a
  // wrong custodian input on any earlier day was never looked at - not on the day it landed if the
  // pipeline was behind, and never again afterwards.
  @Test
  void aDifferenceOnAnEarlierDayInTheWindowIsStillReported() {
    givenPositionDates(EARLIER_POSITION_DATE, POSITION_DATE);
    givenComparison(differing(EARLIER_POSITION_DATE, difference(PAYABLES, "-12500.00", "0.00")));
    givenComparison(matching(POSITION_DATE));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.deviationAmount()).isEqualByComparingTo("12500.00");
    assertThat(finding.message()).contains(EARLIER_POSITION_DATE.toString());
  }

  @Test
  void everyDifferingDayInTheWindowIsCounted() {
    givenPositionDates(EARLIER_POSITION_DATE, POSITION_DATE);
    givenComparison(differing(EARLIER_POSITION_DATE, difference(PAYABLES, "-12500.00", "0.00")));
    givenComparison(differing(POSITION_DATE, difference(PAYABLES, "-12500.00", "0.00")));

    var finding = check().getFirst();

    assertThat(finding.deviationAmount()).isEqualByComparingTo("25000.00");
    assertThat(finding.message()).contains(EARLIER_POSITION_DATE.toString(), "2 day(s)");
  }

  // A day with no nav_report is a gap in coverage, not a deviation, but it must not hide a real
  // mismatch found on another day in the same window.
  @Test
  void aMismatchOutranksADayThatCouldNotBeCompared() {
    givenPositionDates(EARLIER_POSITION_DATE, POSITION_DATE);
    given(comparator.compare(TUK75, EARLIER_POSITION_DATE)).willReturn(Optional.empty());
    givenComparison(differing(POSITION_DATE, difference(PAYABLES, "-12500.00", "0.00")));

    assertThat(check().getFirst().severity()).isEqualTo(WARNING);
  }

  private List<FeeCheckFinding> check() {
    return checker.check(TUK75, FROM, TO);
  }

  private void givenPositionDates(LocalDate... dates) {
    given(fundPositionRepository.findDistinctNavDatesByFundBetween(TUK75, FROM, TO))
        .willReturn(List.of(dates));
  }

  // "The NAV could not have read it" and "the NAV is fine" are different claims, and the timestamp
  // only proves the first. A re-send that corrects a wrong earlier report looks identical to one
  // carrying a late trade, so a difference big enough to matter stays a warning either way.
  @Test
  void warnsAboutAReSentReportThatWouldHaveMovedTheNavMaterially() {
    givenPositionDates(POSITION_DATE);
    givenComparison(
        lateCorrection(
            POSITION_DATE,
            new BigDecimal("-75247.49"),
            new BigDecimal("-5.00"),
            difference(RECEIVABLES, "325708788.10", "0.00")));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.message())
        .contains("A day marked re-sent post-dates the NAV", "1.00 bp", "-5.00 bp");
  }

  // The re-send sentence is there to stop a reader dismissing a warning they can see is dated
  // after the NAV. On a warning where no day is dated at all it explains nothing and reads as if
  // one were, so it has to be absent.
  @Test
  void saysNothingAboutReSendsOnAWarningWhereNoDayWasReSent() {
    givenPositionDates(POSITION_DATE);
    givenComparison(differing(POSITION_DATE, difference(PAYABLES, "-12500.00", "0.00")));

    assertThat(check().getFirst().message()).doesNotContain("re-sent");
  }

  // The event row is the durable record; the Slack message is not queryable and ages out. Keeping
  // only the dates would drop exactly the line detail this check exists to report.
  @Test
  void keepsTheDifferingLinesInTheFindingDetails() {
    givenPositionDates(POSITION_DATE);
    givenComparison(differing(POSITION_DATE, difference(RECEIVABLES, "325708788.10", "0.00")));

    var finding = check().getFirst();

    assertThat(finding.details().get("lines"))
        .asInstanceOf(LIST)
        .singleElement()
        .asString()
        .contains(POSITION_DATE.toString(), RECEIVABLES, "325708788.10", "0.00");
  }

  private void givenComparison(CustodianDayComparison comparison) {
    given(comparator.compare(TUK75, comparison.navDate())).willReturn(Optional.of(comparison));
  }

  private CustodianLineDifference difference(String accountName, String report, String nav) {
    return new CustodianLineDifference(accountName, new BigDecimal(report), new BigDecimal(nav));
  }

  private CustodianDayComparison matching(LocalDate navDate) {
    return new CustodianDayComparison(navDate, List.of(), false, ZERO, ZERO);
  }

  private CustodianDayComparison differing(
      LocalDate navDate, CustodianLineDifference... differences) {
    return new CustodianDayComparison(navDate, List.of(differences), false, ZERO, ZERO);
  }

  private CustodianDayComparison lateCorrection(
      LocalDate navDate,
      BigDecimal navImpact,
      BigDecimal basisPoints,
      CustodianLineDifference... differences) {
    return new CustodianDayComparison(navDate, List.of(differences), true, navImpact, basisPoints);
  }
}
