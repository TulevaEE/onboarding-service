package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.FundCheck.NavNotDueYet;
import ee.tuleva.onboarding.investment.check.tracking.FundCheck.NeverCheckable;
import ee.tuleva.onboarding.investment.check.tracking.FundCheck.NotCheckable;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MissingNavClassifier {

  private final Clock clock;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final FundNavQueryService fundNavQueryService;
  private final PublicHolidays publicHolidays;

  FundCheck classify(
      TulevaFund fund, LocalDate checkDate, LocalDate previousDate, boolean noNavOnTheCheckDate) {
    if (hasNoModelPortfolio(fund, checkDate) || hasNoEarlierNavToCompareWith(fund, previousDate)) {
      return new NeverCheckable();
    }
    if (noNavOnTheCheckDate) {
      return noNavForTheCheckDate(checkDate);
    }
    return new NotCheckable("no NAV for the working day before, " + previousDate);
  }

  private boolean hasNoModelPortfolio(TulevaFund fund, LocalDate checkDate) {
    return modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, checkDate).isEmpty();
  }

  private boolean hasNoEarlierNavToCompareWith(TulevaFund fund, LocalDate previousDate) {
    return fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), previousDate).isEmpty();
  }

  private FundCheck noNavForTheCheckDate(LocalDate checkDate) {
    if (!publicHolidays.isWorkingDay(checkDate)) {
      return new NeverCheckable();
    }
    return navIsDue(checkDate) ? new NotCheckable("no NAV for the check date") : new NavNotDueYet();
  }

  private boolean navIsDue(LocalDate navDate) {
    return !LocalDate.now(clock).isBefore(publicHolidays.nextWorkingDay(navDate));
  }
}
