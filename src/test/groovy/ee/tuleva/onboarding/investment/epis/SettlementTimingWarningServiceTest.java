package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.TUK00_ACTIVE;
import static ee.tuleva.onboarding.investment.epis.SettlementTimingWarning.Type.PEVA_DEADLINE_MISS;
import static ee.tuleva.onboarding.investment.epis.SettlementTimingWarning.Type.REBALANCE_GAP;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.SettlementDateCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SettlementTimingWarningServiceTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 4, 20);
  private static final LocalDate LOCK_DATE = LocalDate.of(2026, 3, 31);
  private static final LocalDate EXEC_DATE = LocalDate.of(2026, 5, 1);

  private final PevaRavaPeriodService periodService = mock(PevaRavaPeriodService.class);
  private final SettlementDateCalculator settlementDateCalculator =
      mock(SettlementDateCalculator.class);
  private final ModelPortfolioAllocationRepository allocationRepository =
      mock(ModelPortfolioAllocationRepository.class);
  private final Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  private final SettlementTimingWarningService service =
      new SettlementTimingWarningService(
          periodService, settlementDateCalculator, allocationRepository, clock);

  @Test
  void noWarningsWhenNoCurrentPeriod() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.empty());

    assertThat(service.activeWarnings()).isEmpty();
  }

  @Test
  void noWarningsWhenPhaseDone() {
    given(periodService.getCurrentPeriod(TODAY))
        .willReturn(Optional.of(period(DONE, dActive(), dActive())));

    assertThat(service.activeWarnings()).isEmpty();
  }

  @Test
  void noWarningsAfterExecDate() {
    Clock afterExec = Clock.fixed(EXEC_DATE.plusDays(1).atStartOfDay(TALLINN).toInstant(), TALLINN);
    SettlementTimingWarningService service =
        new SettlementTimingWarningService(
            periodService, settlementDateCalculator, allocationRepository, afterExec);
    given(periodService.getCurrentPeriod(EXEC_DATE.plusDays(1)))
        .willReturn(Optional.of(period(ACTIVE, dActive(), dActive())));

    assertThat(service.activeWarnings()).isEmpty();
  }

  @Test
  void noWarningsWhenNoFundIsDActive() {
    given(periodService.getCurrentPeriod(TODAY))
        .willReturn(Optional.of(period(PevaRavaPhase.DATA_VALID, notDActive(), notDActive())));

    assertThat(service.activeWarnings()).isEmpty();
  }

  @Test
  void warnsOnDeadlineMissAndRebalanceGapUsingWorstFundInstrumentSettlement() {
    given(periodService.getCurrentPeriod(TODAY))
        .willReturn(Optional.of(period(TUK00_ACTIVE, notDActive(), dActive())));
    given(allocationRepository.findLatestByFundAsOf(TUK00, TODAY))
        .willReturn(
            List.of(
                allocation(TUK00, "LU0000000001", FUND),
                allocation(TUK00, "LU0000000002", FUND),
                allocation(TUK00, "IE0000000001", ETF),
                allocation(TUK00, null, FUND)));
    given(settlementDateCalculator.calculateSettlementDate(TODAY, FUND, "LU0000000001"))
        .willReturn(LocalDate.of(2026, 4, 27));
    given(settlementDateCalculator.calculateSettlementDate(TODAY, FUND, "LU0000000002"))
        .willReturn(LocalDate.of(2026, 5, 4));
    given(settlementDateCalculator.calculateSettlementDate(TODAY, ETF, TUK00.getIsin()))
        .willReturn(LocalDate.of(2026, 4, 22));

    assertThat(service.activeWarnings())
        .containsExactly(
            new SettlementTimingWarning(
                PEVA_DEADLINE_MISS,
                TUK00,
                LocalDate.of(2026, 5, 4),
                EXEC_DATE,
                "FUND sell placed today settles after PEVA/RAVA execution: fund=TUK00,"
                    + " sellSettlementDate=2026-05-04, execDate=2026-05-01"),
            new SettlementTimingWarning(
                REBALANCE_GAP,
                TUK00,
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 4, 22),
                "FUND sell settles after same-day ETF buy: fund=TUK00,"
                    + " sellSettlementDate=2026-05-04, etfBuySettlementDate=2026-04-22"));
  }

  @Test
  void noDeadlineMissWhenSellSettlesOnOrBeforeExecDate() {
    given(periodService.getCurrentPeriod(TODAY))
        .willReturn(Optional.of(period(TUK00_ACTIVE, notDActive(), dActive())));
    given(allocationRepository.findLatestByFundAsOf(TUK00, TODAY))
        .willReturn(List.of(allocation(TUK00, "LU0000000001", FUND)));
    given(settlementDateCalculator.calculateSettlementDate(TODAY, FUND, "LU0000000001"))
        .willReturn(LocalDate.of(2026, 4, 27));
    given(settlementDateCalculator.calculateSettlementDate(TODAY, ETF, TUK00.getIsin()))
        .willReturn(LocalDate.of(2026, 4, 22));

    assertThat(service.activeWarnings())
        .containsExactly(
            new SettlementTimingWarning(
                REBALANCE_GAP,
                TUK00,
                LocalDate.of(2026, 4, 27),
                LocalDate.of(2026, 4, 22),
                "FUND sell settles after same-day ETF buy: fund=TUK00,"
                    + " sellSettlementDate=2026-04-27, etfBuySettlementDate=2026-04-22"));
  }

  @Test
  void noWarningsForDActiveFundWithoutFundTypeInstruments() {
    given(periodService.getCurrentPeriod(TODAY))
        .willReturn(Optional.of(period(ACTIVE, dActive(), notDActive())));
    given(allocationRepository.findLatestByFundAsOf(TUK75, TODAY))
        .willReturn(List.of(allocation(TUK75, "IE0000000001", ETF)));

    assertThat(service.activeWarnings()).isEmpty();
  }

  private static PevaRavaPeriod period(
      PevaRavaPhase phase, FundCycleTimeline tuk75, FundCycleTimeline tuk00) {
    return new PevaRavaPeriod(phase, new PevaRavaCycle(LOCK_DATE, EXEC_DATE), tuk75, tuk00);
  }

  private static FundCycleTimeline dActive() {
    return new FundCycleTimeline(TODAY.minusDays(1), EXEC_DATE.minusDays(5), true, false);
  }

  private static FundCycleTimeline notDActive() {
    return new FundCycleTimeline(TODAY.plusDays(2), EXEC_DATE.minusDays(5), false, false);
  }

  private static ModelPortfolioAllocation allocation(
      TulevaFund fund, @Nullable String isin, InstrumentType instrumentType) {
    return ModelPortfolioAllocation.builder()
        .fund(fund)
        .isin(isin)
        .instrumentType(instrumentType)
        .weight(BigDecimal.ONE)
        .effectiveDate(LocalDate.of(2026, 1, 1))
        .build();
  }
}
