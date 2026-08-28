package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// The numbers are the real TUK75 CCF-to-Amundi switch on 2026-08-26, where SEB re-sent the report
// with the CCF redemption booked after the NAV had already been calculated from the earlier one.
@ExtendWith(MockitoExtension.class)
class CustodianPositionComparatorTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 8, 26);
  private static final Instant CALCULATED_AT = Instant.parse("2026-08-26T15:30:00Z");
  private static final Instant BEFORE_CALCULATION = Instant.parse("2026-08-26T09:00:00Z");
  private static final Instant AFTER_CALCULATION = Instant.parse("2026-08-27T09:00:00Z");

  private static final String CASH_ACCOUNT = "Cash account in SEB Pank";
  private static final String TRADE_RECEIVABLES = "Total receivables of unsettled transactions";
  private static final String TRADE_PAYABLES = "Total payables of unsettled transactions";
  private static final String CCF_ISIN = "IE0009FT4LX4";

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private FundNavQueryService fundNavQueryService;

  private CustodianPositionComparator comparator;

  @BeforeEach
  void setUp() {
    comparator =
        new CustodianPositionComparator(
            fundPositionRepository, fundNavQueryService, new BigDecimal("1.00"));
  }

  @Test
  void aDateWithoutANavCalculationCannotBeCompared() {
    given(fundNavQueryService.findLatestCalculation("TUK75", NAV_DATE))
        .willReturn(Optional.empty());

    assertThat(comparator.compare(TUK75, NAV_DATE)).isEmpty();
  }

  @Test
  void aReportMatchingTheNavHasNoDifferingLines() {
    givenNav(reSentNavLines());
    givenPositions(reSentPositions());
    givenPositionWrittenAt(BEFORE_CALCULATION);

    assertThat(comparator.compare(TUK75, NAV_DATE))
        .get()
        .returns(true, CustodianDayComparison::matches);
  }

  @Test
  void onlyTheLineThatActuallyMovedIsReported() {
    givenNav(originalNavLines());
    givenPositions(reSentPositions());
    givenPositionWrittenAt(AFTER_CALCULATION);

    var differences = comparator.compare(TUK75, NAV_DATE).orElseThrow().differences();

    assertThat(differences)
        .singleElement()
        .returns(TRADE_RECEIVABLES, CustodianLineDifference::accountName)
        .returns(new BigDecimal("325708788.10"), CustodianLineDifference::report)
        .returns(new BigDecimal("0"), CustodianLineDifference::nav);
  }

  @Test
  void aDifferenceWithinToleranceIsNotReported() {
    givenNav(reSentNavLines());
    givenPositions(withCashOf(reSentPositions(), "738962.19"));
    givenPositionWrittenAt(BEFORE_CALCULATION);

    assertThat(comparator.compare(TUK75, NAV_DATE))
        .get()
        .returns(true, CustodianDayComparison::matches);
  }

  // The netted cash/receivable/payable difference is +325 708 788.10, but the fund is only worth
  // 75 247.49 less: the redemption came in below what the NAV still valued the holding at. Only
  // reading the security leg as well turns the alert into a number worth deciding on.
  @Test
  void theNavImpactNetsTheSecurityLegAgainstTheCashLeg() {
    givenNav(originalNavLines());
    givenPositions(reSentPositions());
    givenPositionWrittenAt(AFTER_CALCULATION);

    var comparison = comparator.compare(TUK75, NAV_DATE).orElseThrow();

    assertThat(comparison.navImpact()).isEqualByComparingTo("-75247.49");
    assertThat(comparison.navImpactBasisPoints()).isEqualByComparingTo("-0.67");
  }

  @Test
  void aPositionWrittenAfterTheCalculationMarksTheNavAsPredatingTheReport() {
    givenNav(originalNavLines());
    givenPositions(reSentPositions());
    givenPositionWrittenAt(AFTER_CALCULATION);

    assertThat(comparator.compare(TUK75, NAV_DATE).orElseThrow().navPredatesReport()).isTrue();
  }

  @Test
  void aPositionWrittenBeforeTheCalculationIsAnInputTheNavShouldHaveUsed() {
    givenNav(originalNavLines());
    givenPositions(reSentPositions());
    givenPositionWrittenAt(BEFORE_CALCULATION);

    assertThat(comparator.compare(TUK75, NAV_DATE).orElseThrow().navPredatesReport()).isFalse();
  }

  @Test
  void aPositionWithNoWriteTimeAtAllIsNotTreatedAsALateCorrection() {
    givenNav(originalNavLines());
    givenPositions(reSentPositions());
    given(fundPositionRepository.findLastWrittenAt(TUK75, NAV_DATE)).willReturn(Optional.empty());

    assertThat(comparator.compare(TUK75, NAV_DATE).orElseThrow().navPredatesReport()).isFalse();
  }

  // The custodian publishes prices rounded to three decimals while the NAV values the same holding
  // at five, so comparing market values would report a phantom difference on every mutual fund.
  @Test
  void aCustodianPriceRoundedToFewerDecimalsDoesNotMoveTheNavImpact() {
    var navLines = new ArrayList<>(reSentNavLines());
    navLines.add(security("IE00BFG1TM61", "8536934.4", "38.5073", "328734294.00"));
    givenNav(navLines);

    var positions = new ArrayList<>(reSentPositions());
    positions.add(
        position(SECURITY, "iShares DW", "IE00BFG1TM61", "8536934.4", "38.507", "328731732.90"));
    givenPositions(positions);
    givenPositionWrittenAt(BEFORE_CALCULATION);

    assertThat(comparator.compare(TUK75, NAV_DATE).orElseThrow().navImpact())
        .isEqualByComparingTo("0");
  }

  private void givenNav(List<NavAccountLine> lines) {
    given(fundNavQueryService.findLatestCalculation("TUK75", NAV_DATE))
        .willReturn(Optional.of(new NavCalculation(CALCULATED_AT, lines)));
  }

  private void givenPositions(List<FundPosition> positions) {
    given(fundPositionRepository.findCustodianSourced(TUK75, NAV_DATE, TUK75.getIsin()))
        .willReturn(positions);
  }

  private void givenPositionWrittenAt(Instant writtenAt) {
    given(fundPositionRepository.findLastWrittenAt(TUK75, NAV_DATE))
        .willReturn(Optional.of(writtenAt));
  }

  private List<NavAccountLine> originalNavLines() {
    return List.of(
        navLine("CASH", CASH_ACCOUNT, "738961.19"),
        navLine("RECEIVABLES", TRADE_RECEIVABLES, "0"),
        navLine("LIABILITY", TRADE_PAYABLES, "-320994863.36"),
        security(CCF_ISIN, "18811874.096", "17.318", "325784035.59"),
        navLine("UNITS", "Total outstanding units:", "1122244468.36"));
  }

  private List<NavAccountLine> reSentNavLines() {
    return List.of(
        navLine("CASH", CASH_ACCOUNT, "738961.19"),
        navLine("RECEIVABLES", TRADE_RECEIVABLES, "325708788.10"),
        navLine("LIABILITY", TRADE_PAYABLES, "-320994863.36"),
        security(CCF_ISIN, "0", "17.318", "0"),
        navLine("UNITS", "Total outstanding units:", "1122244468.36"));
  }

  private List<FundPosition> reSentPositions() {
    return List.of(
        position(CASH, CASH_ACCOUNT, null, null, null, "738961.19"),
        position(RECEIVABLES, TRADE_RECEIVABLES, null, null, null, "325708788.10"),
        position(LIABILITY, TRADE_PAYABLES, null, null, null, "-320994863.36"),
        position(SECURITY, "CCF Developed World", CCF_ISIN, "0", "17.318", "0"));
  }

  private List<FundPosition> withCashOf(List<FundPosition> positions, String cash) {
    return positions.stream()
        .map(
            position ->
                CASH_ACCOUNT.equals(position.getAccountName())
                    ? position(CASH, CASH_ACCOUNT, null, null, null, cash)
                    : position)
        .toList();
  }

  private NavAccountLine navLine(String accountType, String accountName, String marketValue) {
    return new NavAccountLine(
        accountType, accountName, null, null, null, new BigDecimal(marketValue));
  }

  private NavAccountLine security(String isin, String quantity, String price, String marketValue) {
    return new NavAccountLine(
        "SECURITY",
        isin,
        isin,
        new BigDecimal(quantity),
        new BigDecimal(price),
        new BigDecimal(marketValue));
  }

  private FundPosition position(
      AccountType accountType,
      String accountName,
      String accountId,
      String quantity,
      String price,
      String marketValue) {
    return FundPosition.builder()
        .fund(TUK75)
        .navDate(NAV_DATE)
        .accountType(accountType)
        .accountName(accountName)
        .accountId(accountId)
        .quantity(quantity == null ? null : new BigDecimal(quantity))
        .marketPrice(price == null ? null : new BigDecimal(price))
        .marketValue(new BigDecimal(marketValue))
        .build();
  }
}
