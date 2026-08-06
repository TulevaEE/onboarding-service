package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
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
class CustodianCompletenessCheckerTest {

  private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
  private static final LocalDate TO = LocalDate.of(2026, 6, 4);
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
    givenPositionDate(POSITION_DATE);
    givenCustodianTotal(RECOGNISED);
    givenRecognisedTotal(RECOGNISED);

    assertThat(check()).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  @Test
  void aLiabilityRowTheLedgerNeverRecognisedIsReported() {
    var unrecognisedLiability = new BigDecimal("-12500.00");
    givenPositionDate(POSITION_DATE);
    givenCustodianTotal(RECOGNISED.add(unrecognisedLiability));
    givenRecognisedTotal(RECOGNISED);

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.deviationAmount()).isEqualByComparingTo(unrecognisedLiability.abs());
    assertThat(finding.message()).contains(POSITION_DATE.toString());
  }

  @Test
  void aDifferenceWithinToleranceStillPasses() {
    givenPositionDate(POSITION_DATE);
    givenCustodianTotal(RECOGNISED.add(new BigDecimal("1.00")));
    givenRecognisedTotal(RECOGNISED);

    assertThat(check().getFirst().severity()).isEqualTo(PASS);
  }

  @Test
  void noPositionReportAtAllIsNotRunRatherThanADeviation() {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, TO))
        .willReturn(Optional.empty());

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.deviationAmount()).isNull();
  }

  @Test
  void aPositionReportOlderThanTheCheckWindowIsNotRun() {
    givenPositionDate(FROM.minusDays(1));

    assertThat(check().getFirst().severity()).isEqualTo(NOT_RUN);
  }

  @Test
  void aPositionDateWithoutANavReportIsNotRun() {
    givenPositionDate(POSITION_DATE);
    given(fundNavQueryService.findCustodianComparableTotal("TUK75", POSITION_DATE))
        .willReturn(Optional.empty());

    assertThat(check().getFirst().severity()).isEqualTo(NOT_RUN);
  }

  private List<FeeCheckFinding> check() {
    return checker.check(TUK75, FROM, TO);
  }

  private void givenPositionDate(LocalDate date) {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, TO))
        .willReturn(Optional.of(date));
  }

  private void givenCustodianTotal(BigDecimal total) {
    given(fundPositionRepository.sumCustodianMarketValue(any(), any(), any(), any()))
        .willReturn(total);
  }

  private void givenRecognisedTotal(BigDecimal total) {
    given(fundNavQueryService.findCustodianComparableTotal("TUK75", POSITION_DATE))
        .willReturn(Optional.of(total));
  }
}
