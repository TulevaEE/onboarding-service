package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.savings.NavPublication.shown;
import static ee.tuleva.onboarding.savings.TradeDay.Day.DAY_OFF;
import static ee.tuleva.onboarding.savings.TradeDay.Day.WORKING_DAY;
import static java.math.RoundingMode.HALF_UP;
import static java.math.RoundingMode.UNNECESSARY;

import ee.tuleva.onboarding.account.SavingsFundNav;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.NavPublication.Moment;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundNavProvider implements SavingsFundNav {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final BigDecimal MAX_DAILY_CHANGE = new BigDecimal("0.20");

  private final FundValueQueries fundValueQueries;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Override
  public BigDecimal getDisplayNav(TulevaFund fund) {
    String isin = fund.getIsin();
    LocalDate safeDate = safeMaxNavDate();
    BigDecimal nav =
        fundValueQueries
            .getLatestValue(isin, safeDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "NAV not found for savings fund: isin="
                            + isin
                            + ", safeMaxDate="
                            + safeDate))
            .value();
    return nav.stripTrailingZeros().setScale(fund.getNavScale(), UNNECESSARY);
  }

  public BigDecimal getVerifiedNavForIssuingAndRedeeming(TulevaFund fund, LocalDate dealingDate) {
    String isin = fund.getIsin();
    FundValue fundValue =
        fundValueQueries
            .getLatestValue(isin, dealingDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "NAV not found for savings fund: isin="
                            + isin
                            + ", expectedDate="
                            + dealingDate));

    if (!fundValue.date().equals(dealingDate)) {
      throw new IllegalStateException(
          "Stale NAV for savings fund: isin="
              + isin
              + ", expectedDate="
              + dealingDate
              + ", actualDate="
              + fundValue.date());
    }

    BigDecimal nav = fundValue.value();
    if (nav.stripTrailingZeros().scale() > fund.getNavScale()) {
      throw new IllegalStateException(
          "Unexpected NAV scale for savings fund: isin="
              + isin
              + ", nav="
              + nav
              + ", scale="
              + nav.stripTrailingZeros().scale());
    }

    nav = nav.stripTrailingZeros().setScale(fund.getNavScale(), UNNECESSARY);

    validateReasonableChange(isin, nav, dealingDate);

    return nav;
  }

  private void validateReasonableChange(String isin, BigDecimal nav, LocalDate navDate) {
    LocalDate previousDate = publicHolidays.previousWorkingDay(navDate);
    fundValueQueries
        .getLatestValue(isin, previousDate)
        .ifPresent(
            previousValue -> {
              BigDecimal change =
                  nav.subtract(previousValue.value())
                      .divide(previousValue.value(), 4, HALF_UP)
                      .abs();
              if (change.compareTo(MAX_DAILY_CHANGE) > 0) {
                throw new IllegalStateException(
                    "NAV change exceeds safety threshold: isin="
                        + isin
                        + ", previousNav="
                        + previousValue.value()
                        + ", nav="
                        + nav
                        + ", change="
                        + change);
              }
            });
  }

  @Override
  public LocalDate safeMaxNavDate() {
    var now = clock.instant().atZone(ESTONIAN_ZONE);
    LocalDate today = now.toLocalDate();
    LocalTime time = now.toLocalTime();

    LocalDate previousWorkingDay = publicHolidays.previousWorkingDay(today);
    var moment = new Moment(publicHolidays.isWorkingDay(today) ? WORKING_DAY : DAY_OFF, time);
    return switch (shown(moment)) {
      case PUBLISHED_TODAY -> previousWorkingDay;
      case PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY ->
          publicHolidays.previousWorkingDay(previousWorkingDay);
    };
  }
}
