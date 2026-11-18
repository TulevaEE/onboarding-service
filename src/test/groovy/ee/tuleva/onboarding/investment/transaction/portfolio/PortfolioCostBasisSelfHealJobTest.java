package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.SELF_HEAL_DAYS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioCostBasisSelfHealJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);

  @Spy private Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  @Mock private PortfolioCostBasisService service;
  @Mock private PortfolioBaselineRepository baselineRepository;
  @Mock private CostBasisOversellAlerter oversellAlerter;

  @InjectMocks private PortfolioCostBasisSelfHealJob job;

  @Test
  void run_rebuildsLastFourteenDaysForFundsWithBaseline() {
    PortfolioBaseline baseline =
        PortfolioBaseline.builder().fundIsin(TUK75.getIsin()).baselineDate(TODAY).build();
    given(baselineRepository.findByFundIsin(TUK75.getIsin())).willReturn(Optional.of(baseline));
    for (TulevaFund fund : TulevaFund.values()) {
      if (fund != TUK75) {
        given(baselineRepository.findByFundIsin(fund.getIsin())).willReturn(Optional.empty());
      }
    }

    job.run();

    verify(service).rebuildRange(TUK75, TODAY.minusDays(SELF_HEAL_DAYS), TODAY);
    for (TulevaFund fund : TulevaFund.values()) {
      if (fund != TUK75) {
        verify(service, never()).rebuildRange(fund, TODAY.minusDays(SELF_HEAL_DAYS), TODAY);
      }
    }
  }

  @Test
  void onPortfolioCostBasisSelfHealRequested_triggersRun() {
    PortfolioBaseline baseline =
        PortfolioBaseline.builder().fundIsin(TUK75.getIsin()).baselineDate(TODAY).build();
    given(baselineRepository.findByFundIsin(TUK75.getIsin())).willReturn(Optional.of(baseline));
    for (TulevaFund fund : TulevaFund.values()) {
      if (fund != TUK75) {
        given(baselineRepository.findByFundIsin(fund.getIsin())).willReturn(Optional.empty());
      }
    }

    job.onPortfolioCostBasisSelfHealRequested();

    verify(service).rebuildRange(TUK75, TODAY.minusDays(SELF_HEAL_DAYS), TODAY);
  }

  @Test
  void run_recordsAnOversellClearsTheFundsThatRebuiltAndPostsOnce() {
    givenBaselinesFor(TUK75, TUK00);
    LocalDate from = TODAY.minusDays(SELF_HEAL_DAYS);
    CostBasisOversellException oversell =
        new CostBasisOversellException(
            TUK75.getIsin(), "IE00BFNM3G45", from, new BigDecimal("100"), new BigDecimal("150"));
    willThrow(oversell).given(service).rebuildRange(TUK75, from, TODAY);

    job.run();

    InOrder inOrder = inOrder(service, oversellAlerter);
    inOrder.verify(oversellAlerter).recordFound(TUK75, oversell);
    inOrder.verify(service).rebuildRange(TUK00, from, TODAY);
    inOrder.verify(oversellAlerter).clearResolved(TUK00, from, TODAY);
    inOrder.verify(oversellAlerter).postPending();
    verify(oversellAlerter, never()).clearResolved(TUK75, from, TODAY);
  }

  @Test
  void run_neitherRecordsNorClearsAnOversellForAFailureOfAnotherKind() {
    givenBaselinesFor(TUK75);
    LocalDate from = TODAY.minusDays(SELF_HEAL_DAYS);
    willThrow(new IllegalStateException("Execution refers to missing order: orderId=1"))
        .given(service)
        .rebuildRange(TUK75, from, TODAY);

    job.run();

    verify(oversellAlerter, never()).recordFound(any(), any());
    verify(oversellAlerter, never()).clearResolved(TUK75, from, TODAY);
    verify(oversellAlerter).postPending();
  }

  @Test
  void aManualSelfHealTakesTheScheduledRunsLockSoTheyNeverPostTheSameAlertTwice()
      throws NoSuchMethodException {
    SchedulerLock scheduled =
        PortfolioCostBasisSelfHealJob.class.getMethod("run").getAnnotation(SchedulerLock.class);
    SchedulerLock manual =
        PortfolioCostBasisSelfHealJob.class
            .getDeclaredMethod("onPortfolioCostBasisSelfHealRequested")
            .getAnnotation(SchedulerLock.class);

    assertThat(manual.name()).isEqualTo(scheduled.name());
  }

  private void givenBaselinesFor(TulevaFund... fundsWithBaseline) {
    List<TulevaFund> withBaseline = List.of(fundsWithBaseline);
    for (TulevaFund fund : TulevaFund.values()) {
      given(baselineRepository.findByFundIsin(fund.getIsin()))
          .willReturn(
              withBaseline.contains(fund)
                  ? Optional.of(
                      PortfolioBaseline.builder()
                          .fundIsin(fund.getIsin())
                          .baselineDate(TODAY)
                          .build())
                  : Optional.empty());
    }
  }
}
