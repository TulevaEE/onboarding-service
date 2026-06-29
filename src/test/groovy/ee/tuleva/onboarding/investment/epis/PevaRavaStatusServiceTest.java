package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.IGNORE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.TUK00_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PevaRavaStatusServiceTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 4, 20);

  private final PevaRavaPeriodService periodService = mock(PevaRavaPeriodService.class);
  private final PevaRavaFlowService flowService = mock(PevaRavaFlowService.class);
  private final Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  private final PevaRavaStatusService service =
      new PevaRavaStatusService(periodService, flowService, clock);

  @Test
  void statusIncludesPhaseCycleTimelinesAndFlows() {
    PevaRavaCycle cycle = new PevaRavaCycle(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 1));
    FundCycleTimeline tuk75 =
        new FundCycleTimeline(LocalDate.of(2026, 4, 22), LocalDate.of(2026, 4, 27), false, false);
    FundCycleTimeline tuk00 =
        new FundCycleTimeline(LocalDate.of(2026, 4, 14), LocalDate.of(2026, 4, 23), true, false);
    given(periodService.getCurrentPeriod(TODAY))
        .willReturn(Optional.of(new PevaRavaPeriod(TUK00_ACTIVE, cycle, tuk75, tuk00)));
    Map<TulevaFund, PevaRavaFlows> flows =
        Map.of(
            TUK00,
            new PevaRavaFlows(
                new BigDecimal("100.00"),
                new BigDecimal("-50.00"),
                new BigDecimal("200.00"),
                new BigDecimal("350.00"),
                new BigDecimal("350.00"),
                new BigDecimal("355000"),
                new BigDecimal("360000")));
    given(flowService.calculateFlows(TODAY)).willReturn(flows);

    assertThat(service.status())
        .isEqualTo(new PevaRavaStatus(TUK00_ACTIVE, cycle, tuk75, tuk00, flows));
  }

  @Test
  void statusWithoutCurrentPeriodIsIgnorePhaseWithoutFlows() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.empty());

    assertThat(service.status()).isEqualTo(new PevaRavaStatus(IGNORE, null, null, null, Map.of()));
    verifyNoInteractions(flowService);
  }

  @Test
  void recalculateReturnsFreshFlows() {
    Map<TulevaFund, PevaRavaFlows> flows =
        Map.of(
            TUK75,
            new PevaRavaFlows(
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("5000"),
                new BigDecimal("5000")));
    given(flowService.calculateFlows(TODAY)).willReturn(flows);

    assertThat(service.recalculate()).isEqualTo(flows);
  }
}
