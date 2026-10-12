package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.MONTH_END_NAV_NOT_PUBLISHED;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class MonthEndNavCheck {

  private final PublicHolidays publicHolidays;

  List<OcfGap> gapsOf(TulevaFund fund, LocalDate monthEnd, LocalDate navDate) {
    var monthEndNavDate = publicHolidays.lastWorkingDayOf(YearMonth.from(monthEnd));
    if (!navDate.isBefore(monthEndNavDate)) {
      return List.of();
    }
    log.warn(
        "Month-end NAV not yet published, underlying fund cost weighs an earlier NAV's holdings:"
            + " fund={}, monthEnd={}, monthEndNavDate={}, navDate={}",
        fund.getCode(),
        monthEnd,
        monthEndNavDate,
        navDate);
    return List.of(MONTH_END_NAV_NOT_PUBLISHED);
  }
}
