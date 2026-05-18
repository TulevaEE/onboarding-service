package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioCostBasisJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);

  @Spy private Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  @Mock private PortfolioCostBasisService service;

  @InjectMocks private PortfolioCostBasisJob job;

  @Test
  void run_invokesServiceForEachFundAtToday() {
    job.run();

    for (TulevaFund fund : new TulevaFund[] {TUK75, TUK00, TUV100, TKF100}) {
      verify(service).runForFundAndDate(fund, TODAY);
    }
  }

  @Test
  void run_continuesAfterServiceException() {
    willThrow(new RuntimeException("boom")).given(service).runForFundAndDate(TUK75, TODAY);

    job.run();

    verify(service).runForFundAndDate(TUK75, TODAY);
    verify(service).runForFundAndDate(TUK00, TODAY);
    verify(service).runForFundAndDate(TUV100, TODAY);
    verify(service).runForFundAndDate(TKF100, TODAY);
  }
}
