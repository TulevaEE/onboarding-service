package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TradePrepOverridesFixture.noOverrides;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.HoldingMismatch;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoRegistryHoldings;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoReportSecurities;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.UnquantifiedReportHoldings;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.Compared;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.HoldingDifference;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.RegistryHasNoHoldings;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportHasNoSecurities;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportRowsWithoutQuantity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradePrepPositionGateTest {

  private static final LocalDate POSITION_DATE = LocalDate.of(2026, 11, 17);

  @Mock private PositionReconciliation positionReconciliation;

  @InjectMocks private TradePrepPositionGate gates;

  @Test
  void eachHoldingTheRegistryAndTheSebReportDisagreeOnIsALine() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of()))
        .willReturn(
            new Compared(
                List.of(
                    new HoldingDifference("A", new BigDecimal("1000"), new BigDecimal("990")),
                    new HoldingDifference("B", ZERO, new BigDecimal("40")))));

    assertThat(gates.check(TKF100, POSITION_DATE, noOverrides()).failures())
        .containsExactly(
            new HoldingMismatch("A", POSITION_DATE, new BigDecimal("1000"), new BigDecimal("990")),
            new HoldingMismatch("B", POSITION_DATE, ZERO, new BigDecimal("40")));
  }

  @Test
  void aRegistryOrReportWithoutHoldingsIsOneLineNotOnePerHolding() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of()))
        .willReturn(
            new RegistryHasNoHoldings(List.of(new HoldingDifference("A", null, ONE))),
            new ReportHasNoSecurities());

    assertThat(gates.check(TKF100, POSITION_DATE, noOverrides()).failures())
        .containsExactly(new NoRegistryHoldings(POSITION_DATE));
    assertThat(gates.check(TKF100, POSITION_DATE, noOverrides()).failures())
        .containsExactly(new NoReportSecurities(POSITION_DATE));
  }

  @Test
  void securitiesWithoutAQuantityAreOneLineNamingThem() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of()))
        .willReturn(new ReportRowsWithoutQuantity(List.of("A", "B")));

    assertThat(gates.check(TKF100, POSITION_DATE, noOverrides()).failures())
        .containsExactly(new UnquantifiedReportHoldings(POSITION_DATE, List.of("A", "B")));
  }

  @Test
  void aHoldingMissingFromOneSideKeepsThatSideMissing() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of("X")))
        .willReturn(new Compared(List.of(new HoldingDifference("A", new BigDecimal("5"), null))));

    assertThat(
            gates
                .check(
                    TKF100,
                    POSITION_DATE,
                    new TradePrepOverrides(
                        Set.of("X"),
                        List.of(),
                        null,
                        PriceDateRule.FRESHEST_PER_INSTRUMENT,
                        null,
                        Set.of()))
                .failures())
        .containsExactly(new HoldingMismatch("A", POSITION_DATE, new BigDecimal("5"), null));
  }

  @Test
  void anAcceptedDifferenceNoLongerStopsTheFundButAnotherOneStill() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of()))
        .willReturn(
            new Compared(
                List.of(
                    new HoldingDifference("A", new BigDecimal("1000"), new BigDecimal("990")),
                    new HoldingDifference("B", new BigDecimal("5"), null))));

    var check =
        gates.check(
            TKF100,
            POSITION_DATE,
            new TradePrepOverrides(
                Set.of(),
                List.of(),
                new AcceptedHoldingDifferences(Set.of("A", "C"), "Booked late"),
                PriceDateRule.FRESHEST_PER_INSTRUMENT,
                null,
                Set.of()));

    assertThat(check)
        .isEqualTo(
            new PositionCheck(
                List.of(new HoldingMismatch("B", POSITION_DATE, new BigDecimal("5"), null)),
                List.of(
                    new AcceptedHoldingDifference(
                        "A",
                        GateNeed.CLEARED_A_STOP,
                        new BigDecimal("1000"),
                        new BigDecimal("990")),
                    new AcceptedHoldingDifference("C", GateNeed.NOT_NEEDED, null, null))));
  }

  @Test
  void aRegistryWithoutHoldingsPassesOnlyWhenEveryDifferenceIsAccepted() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of()))
        .willReturn(
            new RegistryHasNoHoldings(
                List.of(
                    new HoldingDifference("A", null, ONE), new HoldingDifference("B", null, ONE))));

    assertThat(
            gates
                .check(
                    TKF100,
                    POSITION_DATE,
                    new TradePrepOverrides(
                        Set.of(),
                        List.of(),
                        new AcceptedHoldingDifferences(Set.of("A"), "Late"),
                        PriceDateRule.FRESHEST_PER_INSTRUMENT,
                        null,
                        Set.of()))
                .failures())
        .containsExactly(new NoRegistryHoldings(POSITION_DATE));
    assertThat(
            gates
                .check(
                    TKF100,
                    POSITION_DATE,
                    new TradePrepOverrides(
                        Set.of(),
                        List.of(),
                        new AcceptedHoldingDifferences(Set.of("A", "B"), "Baseline loads tonight"),
                        PriceDateRule.FRESHEST_PER_INSTRUMENT,
                        null,
                        Set.of()))
                .failures())
        .isEmpty();
  }

  @Test
  void aReportWithoutSecuritiesStopsTheFundWhateverIsAccepted() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of()))
        .willReturn(new ReportHasNoSecurities());

    assertThat(
            gates.check(
                TKF100,
                POSITION_DATE,
                new TradePrepOverrides(
                    Set.of(),
                    List.of(),
                    new AcceptedHoldingDifferences(Set.of("A"), "Report is late"),
                    PriceDateRule.FRESHEST_PER_INSTRUMENT,
                    null,
                    Set.of())))
        .isEqualTo(
            new PositionCheck(
                List.of(new NoReportSecurities(POSITION_DATE)),
                List.of(new AcceptedHoldingDifference("A", GateNeed.NOT_NEEDED, null, null))));
  }

  @Test
  void securitiesWithoutAQuantityStopTheFundWhateverIsAccepted() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of()))
        .willReturn(new ReportRowsWithoutQuantity(List.of("A")));

    assertThat(
            gates.check(
                TKF100,
                POSITION_DATE,
                new TradePrepOverrides(
                    Set.of(),
                    List.of(),
                    new AcceptedHoldingDifferences(Set.of("A"), "Late"),
                    PriceDateRule.FRESHEST_PER_INSTRUMENT,
                    null,
                    Set.of())))
        .isEqualTo(
            new PositionCheck(
                List.of(new UnquantifiedReportHoldings(POSITION_DATE, List.of("A"))),
                List.of(new AcceptedHoldingDifference("A", GateNeed.NOT_NEEDED, null, null))));
  }

  @Test
  void matchingHoldingsPass() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE, Set.of()))
        .willReturn(new Compared(List.of()));

    assertThat(gates.check(TKF100, POSITION_DATE, noOverrides()).failures()).isEmpty();
  }
}
