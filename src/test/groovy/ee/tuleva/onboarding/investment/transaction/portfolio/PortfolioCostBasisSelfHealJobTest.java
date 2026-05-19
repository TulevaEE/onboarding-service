package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.SELF_HEAL_DAYS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisSelfHealRequested;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    job.onPortfolioCostBasisSelfHealRequested(new RunPortfolioCostBasisSelfHealRequested());

    verify(service).rebuildRange(TUK75, TODAY.minusDays(SELF_HEAL_DAYS), TODAY);
  }
}
