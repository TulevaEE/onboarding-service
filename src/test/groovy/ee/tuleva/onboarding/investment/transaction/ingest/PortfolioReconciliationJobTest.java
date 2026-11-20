package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh;
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresher;
import ee.tuleva.onboarding.savings.NavCalculationCompleted;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioReconciliationJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 22);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 5, 21);

  @Spy private Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  @Mock private PublicHolidays publicHolidays;
  @Mock private PortfolioReconciliationService service;
  @Mock private CostBasisRefresher costBasisRefresher;

  @InjectMocks private PortfolioReconciliationJob job;

  @Test
  void onNavCalculationCompleted_reconcilesEventFundsForPreviousWorkingDay() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(NAV_DATE);
    given(costBasisRefresher.refresh(TUK75)).willReturn(CostBasisRefresh.refreshed());
    given(costBasisRefresher.refresh(TUK00)).willReturn(CostBasisRefresh.refreshed());

    job.onNavCalculationCompleted(new NavCalculationCompleted(List.of(TUK75, TUK00)));

    verify(service).reconcile(TUK75, NAV_DATE, CostBasisRefresh.refreshed());
    verify(service).reconcile(TUK00, NAV_DATE, CostBasisRefresh.refreshed());
  }

  @Test
  void onNavCalculationCompleted_refreshesEachFundsCostBasisBeforeReconcilingIt() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(NAV_DATE);
    given(costBasisRefresher.refresh(TUK75)).willReturn(CostBasisRefresh.refreshed());
    given(costBasisRefresher.refresh(TUK00)).willReturn(CostBasisRefresh.refreshed());

    job.onNavCalculationCompleted(new NavCalculationCompleted(List.of(TUK75, TUK00)));

    InOrder inOrder = inOrder(costBasisRefresher, service);
    inOrder.verify(costBasisRefresher).refresh(TUK75);
    inOrder.verify(service).reconcile(TUK75, NAV_DATE, CostBasisRefresh.refreshed());
    inOrder.verify(costBasisRefresher).refresh(TUK00);
    inOrder.verify(service).reconcile(TUK00, NAV_DATE, CostBasisRefresh.refreshed());
  }

  @Test
  void onNavCalculationCompleted_comparesTheStoredLedgerWhenTheRefreshCouldNotRun() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(NAV_DATE);
    given(costBasisRefresher.refresh(TUK75)).willReturn(CostBasisRefresh.lockNotTaken());
    given(costBasisRefresher.refresh(TUK00))
        .willReturn(CostBasisRefresh.failed("Cost-basis oversell: fund=EE3600109435"));

    job.onNavCalculationCompleted(new NavCalculationCompleted(List.of(TUK75, TUK00)));

    verify(service).reconcile(TUK75, NAV_DATE, CostBasisRefresh.lockNotTaken());
    verify(service)
        .reconcile(
            TUK00, NAV_DATE, CostBasisRefresh.failed("Cost-basis oversell: fund=EE3600109435"));
  }

  @Test
  void onNavCalculationCompleted_continuesAfterServiceException() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(NAV_DATE);
    given(costBasisRefresher.refresh(TUK75)).willReturn(CostBasisRefresh.refreshed());
    given(costBasisRefresher.refresh(TUK00)).willReturn(CostBasisRefresh.refreshed());
    willThrow(new RuntimeException("boom"))
        .given(service)
        .reconcile(TUK75, NAV_DATE, CostBasisRefresh.refreshed());

    job.onNavCalculationCompleted(new NavCalculationCompleted(List.of(TUK75, TUK00)));

    verify(service).reconcile(TUK75, NAV_DATE, CostBasisRefresh.refreshed());
    verify(service).reconcile(TUK00, NAV_DATE, CostBasisRefresh.refreshed());
  }

  @Test
  void onPortfolioReconciliationRequested_refreshesAndReconcilesAllFundsForPreviousWorkingDay() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(NAV_DATE);
    for (TulevaFund fund : TulevaFund.values()) {
      given(costBasisRefresher.refresh(fund)).willReturn(CostBasisRefresh.refreshed());
    }

    job.onPortfolioReconciliationRequested();

    for (TulevaFund fund : new TulevaFund[] {TUK75, TUK00, TUV100, TKF100}) {
      verify(costBasisRefresher).refresh(fund);
      verify(service).reconcile(fund, NAV_DATE, CostBasisRefresh.refreshed());
    }
  }
}
