package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.HoldingMismatch;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoRegistryHoldings;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoReportSecurities;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.Compared;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.HoldingDifference;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.RegistryHasNoHoldings;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportHasNoSecurities;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE))
        .willReturn(
            new Compared(
                List.of(
                    new HoldingDifference("A", new BigDecimal("1000"), new BigDecimal("990")),
                    new HoldingDifference("B", ZERO, new BigDecimal("40")))));

    assertThat(gates.differencesFromPositionReport(TKF100, POSITION_DATE))
        .containsExactly(
            new HoldingMismatch("A", POSITION_DATE, new BigDecimal("1000"), new BigDecimal("990")),
            new HoldingMismatch("B", POSITION_DATE, ZERO, new BigDecimal("40")));
  }

  @Test
  void aRegistryOrReportWithoutHoldingsIsOneLineNotOnePerHolding() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE))
        .willReturn(new RegistryHasNoHoldings(), new ReportHasNoSecurities());

    assertThat(gates.differencesFromPositionReport(TKF100, POSITION_DATE))
        .containsExactly(new NoRegistryHoldings(POSITION_DATE));
    assertThat(gates.differencesFromPositionReport(TKF100, POSITION_DATE))
        .containsExactly(new NoReportSecurities(POSITION_DATE));
  }

  @Test
  void matchingHoldingsPass() {
    given(positionReconciliation.againstPositionReport(TKF100, POSITION_DATE))
        .willReturn(new Compared(List.of()));

    assertThat(gates.differencesFromPositionReport(TKF100, POSITION_DATE)).isEmpty();
  }
}
