package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.IGNORE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PevaRavaFlowRecalcJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 4, 20);

  private final PublicHolidays publicHolidays = mock(PublicHolidays.class);
  private final PevaRavaPeriodService periodService = mock(PevaRavaPeriodService.class);
  private final PevaRavaFlowService flowService = mock(PevaRavaFlowService.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  private final PevaRavaFlowRecalcJob job =
      new PevaRavaFlowRecalcJob(
          clock, publicHolidays, periodService, flowService, notificationService);

  @Test
  void skipsOnNonWorkingDay() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(false);

    job.run();

    verifyNoInteractions(flowService, notificationService);
  }

  @Test
  void skipsWhenPhaseIgnore() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    given(periodService.getCurrentPhase(TODAY)).willReturn(IGNORE);

    job.run();

    verifyNoInteractions(flowService, notificationService);
  }

  @Test
  void skipsWhenPhaseDone() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    given(periodService.getCurrentPhase(TODAY)).willReturn(DONE);

    job.run();

    verifyNoInteractions(flowService, notificationService);
  }

  @Test
  void skipsNotificationWhenNoFlows() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    given(periodService.getCurrentPhase(TODAY)).willReturn(ACTIVE);
    given(flowService.calculateFlows(TODAY)).willReturn(Map.of());

    job.run();

    verifyNoInteractions(notificationService);
  }

  @Test
  void recalculatesFlowsAndSendsLiquiditySummary() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    given(periodService.getCurrentPhase(TODAY)).willReturn(ACTIVE);
    given(flowService.calculateFlows(TODAY))
        .willReturn(
            Map.of(
                TUK75,
                flows("25.00", "25.00", "30000"),
                TUK00,
                flows("350.00", "400.00", "405000")));

    job.run();

    verify(notificationService)
        .sendMessage(
            "💶 PEVA/RAVA rahavood (faas ACTIVE) – 2026-04-20\n"
                + "TUK75: likviidsusvajadus 25.00 €, bruto välja 25.00 €, makselimiit 30000 €\n"
                + "TUK00: likviidsusvajadus 350.00 €, bruto välja 400.00 €, makselimiit 405000 €",
            INVESTMENT);
  }

  @Test
  void runsOnRequestedEvent() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(false);

    job.onPevaRavaFlowRecalcRequested();

    verifyNoInteractions(flowService, notificationService);
  }

  private static PevaRavaFlows flows(String liquidity, String grossOut, String paymentLimit) {
    return new PevaRavaFlows(
        BigDecimal.ONE,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal(liquidity),
        new BigDecimal(grossOut),
        new BigDecimal(paymentLimit),
        new BigDecimal(paymentLimit));
  }
}
