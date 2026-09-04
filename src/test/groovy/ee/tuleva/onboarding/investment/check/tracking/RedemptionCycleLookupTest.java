package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.investment.epis.PevaRavaCycle;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlowService;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlows;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionCycleLookupTest {

  private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 9, 1);
  private static final LocalDate ORDINARY_DAY = LocalDate.of(2026, 8, 31);

  @Mock PevaRavaPeriodService periodService;
  @Mock PevaRavaFlowService flowService;

  @InjectMocks RedemptionCycleLookup lookup;

  @Test
  void anOrdinaryDayNeedsNoCycleFiguresAtAll() {
    givenExecutionDates(EXECUTION_DATE);

    var hint = lookup.resolve(TUK75, ORDINARY_DAY);

    assertThat(hint.executionDate()).isFalse();
    assertThat(hint.hasFigures()).isFalse();
    then(flowService).shouldHaveNoInteractions();
  }

  @Test
  void anExecutionDateWithIngestedReportsCarriesTheRavaPayout() {
    givenExecutionDates(EXECUTION_DATE);
    given(flowService.calculateFlows(EXECUTION_DATE))
        .willReturn(
            Map.of(TUK75, flows(new BigDecimal("5928109.00"), new BigDecimal("120000.00"))));

    var hint = lookup.resolve(TUK75, EXECUTION_DATE);

    assertThat(hint.executionDate()).isTrue();
    assertThat(hint.hasFigures()).isTrue();
    assertThat(hint)
        .isEqualTo(
            new RedemptionCycleHint(
                true, new BigDecimal("5928109.00"), new BigDecimal("120000.00")));
  }

  @Test
  void anExecutionDateWithNoIngestedReportsStillReportsTheDate() {
    givenExecutionDates(EXECUTION_DATE);
    given(flowService.calculateFlows(EXECUTION_DATE)).willReturn(Map.of());

    var hint = lookup.resolve(TUK75, EXECUTION_DATE);

    assertThat(hint.executionDate()).isTrue();
    assertThat(hint.hasFigures()).isFalse();
  }

  @Test
  void aFailingCycleLookupDegradesToTheDateAloneRatherThanBreakingTheCheck() {
    givenExecutionDates(EXECUTION_DATE);
    willThrow(new IllegalStateException("no active cycle"))
        .given(flowService)
        .calculateFlows(EXECUTION_DATE);

    var hint = lookup.resolve(TUK75, EXECUTION_DATE);

    assertThat(hint.executionDate()).isTrue();
    assertThat(hint.hasFigures()).isFalse();
  }

  @Test
  void anUnreadableCalendarLeavesTheHintSilentRatherThanFailingTheAlert() {
    willThrow(new IllegalStateException("calendar unavailable"))
        .given(periodService)
        .executionPeriods(2026);

    var hint = lookup.resolve(TUK75, EXECUTION_DATE);

    assertThat(hint.executionDate()).isFalse();
    assertThat(hint.hasFigures()).isFalse();
  }

  private void givenExecutionDates(LocalDate... execDates) {
    given(periodService.executionPeriods(2026))
        .willReturn(
            List.of(execDates).stream()
                .map(date -> new PevaRavaCycle(date.minusMonths(1), date))
                .toList());
  }

  private PevaRavaFlows flows(BigDecimal ravaEur, BigDecimal pikEur) {
    return new PevaRavaFlows(
        pikEur,
        BigDecimal.ZERO,
        ravaEur,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }
}
