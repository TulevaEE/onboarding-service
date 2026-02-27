package ee.tuleva.onboarding.savings.fund.nav;

import static java.math.RoundingMode.HALF_UP;
import static java.math.RoundingMode.UNNECESSARY;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundNavProvider {

  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0);
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final BigDecimal MAX_DAILY_CHANGE = new BigDecimal("0.20");

  private final FundValueRepository fundValueRepository;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  public BigDecimal getDisplayNav(TulevaFund fund) {
    String isin = fund.getIsin();
    LocalDate safeDate = safeMaxNavDate();
    BigDecimal nav =
        fundValueRepository
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

  public BigDecimal getVerifiedNavForIssuingAndRedeeming(TulevaFund fund) {
    String isin = fund.getIsin();
    FundValue fundValue =
        fundValueRepository
            .findLastValueForFund(isin)
            .orElseThrow(
                () -> new IllegalStateException("NAV not found for savings fund: isin=" + isin));

    LocalDate today = clock.instant().atZone(ESTONIAN_ZONE).toLocalDate();
    LocalDate expectedDate = publicHolidays.previousWorkingDay(today);

    if (!fundValue.date().equals(expectedDate)) {
      throw new IllegalStateException(
          "Stale NAV for savings fund: isin="
              + isin
              + ", expectedDate="
              + expectedDate
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

    validateReasonableChange(isin, nav, expectedDate);

    return nav;
  }

  private void validateReasonableChange(String isin, BigDecimal nav, LocalDate navDate) {
    LocalDate previousDate = publicHolidays.previousWorkingDay(navDate);
    fundValueRepository
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

  public LocalDate safeMaxNavDate() {
    var now = clock.instant().atZone(ESTONIAN_ZONE);
    LocalDate today = now.toLocalDate();
    LocalTime time = now.toLocalTime();

    LocalDate previousWorkingDay = publicHolidays.previousWorkingDay(today);

    boolean afterCutoff = publicHolidays.isWorkingDay(today) && !time.isBefore(CUTOFF_TIME);
    if (afterCutoff) {
      return previousWorkingDay;
    }

    return publicHolidays.previousWorkingDay(previousWorkingDay);
  }
}
