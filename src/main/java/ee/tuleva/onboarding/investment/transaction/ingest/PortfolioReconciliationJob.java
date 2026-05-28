package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.NavEventListenerOrder;
import ee.tuleva.onboarding.investment.event.RunPortfolioReconciliationRequested;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationCompleted;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
public class PortfolioReconciliationJob {

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final PortfolioReconciliationService service;

  @EventListener
  @Order(NavEventListenerOrder.PORTFOLIO_RECONCILIATION)
  void onNavCalculationCompleted(NavCalculationCompleted event) {
    LocalDate navDate = publicHolidays.previousWorkingDay(LocalDate.now(clock));
    reconcileFunds(event.funds(), navDate);
  }

  @EventListener
  void onPortfolioReconciliationRequested(RunPortfolioReconciliationRequested event) {
    LocalDate navDate = publicHolidays.previousWorkingDay(LocalDate.now(clock));
    reconcileFunds(Arrays.asList(TulevaFund.values()), navDate);
  }

  private void reconcileFunds(List<TulevaFund> funds, LocalDate navDate) {
    log.info("Running portfolio reconciliation: navDate={}, fundCount={}", navDate, funds.size());
    for (TulevaFund fund : funds) {
      try {
        service.reconcile(fund, navDate);
      } catch (RuntimeException e) {
        log.error(
            "Portfolio reconciliation failed: fundCode={}, navDate={}, error={}",
            fund.getCode(),
            navDate,
            e.getMessage(),
            e);
      }
    }
  }
}
