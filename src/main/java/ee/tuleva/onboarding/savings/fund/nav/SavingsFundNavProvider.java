package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundNavProvider {

  private final FundValueRepository fundValueRepository;
  private final SavingsFundConfiguration configuration;
  private final PublicHolidays publicHolidays;

  public BigDecimal getCurrentNav() {
    return getFundValue().value();
  }

  public BigDecimal getCurrentNavForIssuing() {
    FundValue fundValue = getFundValue();
    LocalDate expectedDate =
        publicHolidays.previousWorkingDay(LocalDate.now(ClockHolder.getClock()));

    if (!fundValue.date().equals(expectedDate)) {
      throw new IllegalStateException(
          "Stale NAV for savings fund: isin="
              + configuration.getIsin()
              + ", expectedDate="
              + expectedDate
              + ", actualDate="
              + fundValue.date());
    }

    BigDecimal nav = fundValue.value();
    if (nav.scale() > 4) {
      throw new IllegalStateException(
          "Unexpected NAV scale for savings fund: isin="
              + configuration.getIsin()
              + ", nav="
              + nav
              + ", scale="
              + nav.scale());
    }

    return nav;
  }

  private FundValue getFundValue() {
    String isin = configuration.getIsin();
    return fundValueRepository
        .findLastValueForFund(isin)
        .orElseThrow(
            () -> new IllegalStateException("NAV not found for savings fund: isin=" + isin));
  }
}
