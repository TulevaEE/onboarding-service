package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.FundCheck.NeverCheckable;
import ee.tuleva.onboarding.investment.check.tracking.FundCheck.NotCheckable;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MissingNavClassifier {

  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final FundNavQueryService fundNavQueryService;
  private final PublicHolidays publicHolidays;

  FundCheck classify(
      TulevaFund fund, LocalDate checkDate, LocalDate previousDate, boolean noNavOnTheCheckDate) {
    if (hasNoModelPortfolio(fund, checkDate)) {
      return new NeverCheckable();
    }
    if (noNavOnTheCheckDate) {
      return noNavForTheCheckDate(checkDate);
    }
    return isTheFundsFirstNavDay(fund, previousDate)
        ? new NeverCheckable()
        : new NotCheckable("no NAV for the working day before, " + previousDate);
  }

  private boolean hasNoModelPortfolio(TulevaFund fund, LocalDate checkDate) {
    return modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, checkDate).isEmpty();
  }

  private boolean isTheFundsFirstNavDay(TulevaFund fund, LocalDate previousDate) {
    return fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), previousDate).isEmpty();
  }

  private FundCheck noNavForTheCheckDate(LocalDate checkDate) {
    return publicHolidays.isWorkingDay(checkDate)
        ? new NotCheckable("no NAV for the check date")
        : new NeverCheckable();
  }
}
