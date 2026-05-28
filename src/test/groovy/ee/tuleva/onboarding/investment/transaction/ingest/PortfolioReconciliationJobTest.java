package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunPortfolioReconciliationRequested;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationCompleted;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @InjectMocks private PortfolioReconciliationJob job;

  @Test
  void onNavCalculationCompleted_reconcilesEventFundsForPreviousWorkingDay() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(NAV_DATE);

    job.onNavCalculationCompleted(new NavCalculationCompleted(List.of(TUK75, TUK00)));

    verify(service).reconcile(TUK75, NAV_DATE);
    verify(service).reconcile(TUK00, NAV_DATE);
  }

  @Test
  void onNavCalculationCompleted_continuesAfterServiceException() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(NAV_DATE);
    willThrow(new RuntimeException("boom")).given(service).reconcile(TUK75, NAV_DATE);

    job.onNavCalculationCompleted(new NavCalculationCompleted(List.of(TUK75, TUK00)));

    verify(service).reconcile(TUK75, NAV_DATE);
    verify(service).reconcile(TUK00, NAV_DATE);
  }

  @Test
  void onPortfolioReconciliationRequested_reconcilesAllFundsForPreviousWorkingDay() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(NAV_DATE);

    job.onPortfolioReconciliationRequested(new RunPortfolioReconciliationRequested());

    for (TulevaFund fund : new TulevaFund[] {TUK75, TUK00, TUV100, TKF100}) {
      verify(service).reconcile(fund, NAV_DATE);
    }
  }
}
