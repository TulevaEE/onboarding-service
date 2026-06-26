package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.epis.R16Phase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.R16Phase.IGNORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class R16StatusServiceTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 11);

  private final R16FlowCalculationService flowCalculationService =
      mock(R16FlowCalculationService.class);
  private final R16PhaseCalculator phaseCalculator = mock(R16PhaseCalculator.class);
  private final Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  private final R16StatusService service =
      new R16StatusService(flowCalculationService, phaseCalculator, clock);

  @Test
  void returnsPerFundPhaseFlowsAndSuppressionStatus() {
    R16FundFlow tuk75Flow = flow(TUK75);
    R16FundFlow tuv100Flow = flow(TUV100);
    given(flowCalculationService.calculateFlows(TUK75, TODAY)).willReturn(Optional.of(tuk75Flow));
    given(flowCalculationService.calculateFlows(TUK00, TODAY)).willReturn(Optional.empty());
    given(flowCalculationService.calculateFlows(TUV100, TODAY)).willReturn(Optional.of(tuv100Flow));
    given(phaseCalculator.phaseFor(tuk75Flow, TODAY)).willReturn(ACTIVE);
    given(phaseCalculator.phaseFor(null, TODAY)).willReturn(IGNORE);
    given(phaseCalculator.phaseFor(tuv100Flow, TODAY)).willReturn(IGNORE);
    given(phaseCalculator.isSuppressedByR45(tuk75Flow)).willReturn(false);
    given(phaseCalculator.isSuppressedByR45(tuv100Flow)).willReturn(true);

    assertThat(service.status())
        .containsExactly(
            new R16FundStatus(
                TUK75,
                ACTIVE,
                new BigDecimal("1000"),
                new BigDecimal("500"),
                new BigDecimal("1200"),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 8),
                false),
            new R16FundStatus(TUK00, IGNORE, null, null, null, null, null, null, false),
            new R16FundStatus(
                TUV100,
                IGNORE,
                new BigDecimal("1000"),
                new BigDecimal("500"),
                new BigDecimal("1200"),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 8),
                true));
  }

  private static R16FundFlow flow(TulevaFund fund) {
    return new R16FundFlow(
        fund,
        new BigDecimal("1000"),
        new BigDecimal("500"),
        new BigDecimal("1200"),
        LocalDate.of(2026, 6, 1),
        LocalDate.of(2026, 6, 15),
        LocalDate.of(2026, 6, 8));
  }
}
