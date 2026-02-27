package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundNavProviderTest {

  private static final String ISIN = "EE0000003283";
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Mock private FundValueRepository fundValueRepository;
  @Mock private PublicHolidays publicHolidays;

  private FundNavProvider provider(String timeInTallinn) {
    var instant = java.time.LocalDateTime.parse(timeInTallinn).atZone(TALLINN).toInstant();
    var clock = Clock.fixed(instant, TALLINN);
    return new FundNavProvider(fundValueRepository, publicHolidays, clock);
  }

  @Test
  void safeMaxNavDate_afterCutoffOnWorkingDay_returnsPreviousWorkingDay() {
    var provider = provider("2025-01-15T17:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);

    assertThat(provider.safeMaxNavDate()).isEqualTo(yesterday);
  }

  @Test
  void safeMaxNavDate_atExactlyCutoffOnWorkingDay_returnsPreviousWorkingDay() {
    var provider = provider("2025-01-15T16:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);

    assertThat(provider.safeMaxNavDate()).isEqualTo(yesterday);
  }

  @Test
  void safeMaxNavDate_beforeCutoffOnWorkingDay_returnsTwoWorkingDaysBack() {
    var provider = provider("2025-01-15T10:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    LocalDate twoDaysBack = LocalDate.of(2025, 1, 13);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);
    when(publicHolidays.previousWorkingDay(yesterday)).thenReturn(twoDaysBack);

    assertThat(provider.safeMaxNavDate()).isEqualTo(twoDaysBack);
  }

  @Test
  void safeMaxNavDate_onSaturday_returnsTwoWorkingDaysBack() {
    var provider = provider("2025-01-18T12:00:00");
    LocalDate saturday = LocalDate.of(2025, 1, 18);
    LocalDate friday = LocalDate.of(2025, 1, 17);
    LocalDate thursday = LocalDate.of(2025, 1, 16);
    when(publicHolidays.isWorkingDay(saturday)).thenReturn(false);
    when(publicHolidays.previousWorkingDay(saturday)).thenReturn(friday);
    when(publicHolidays.previousWorkingDay(friday)).thenReturn(thursday);

    assertThat(provider.safeMaxNavDate()).isEqualTo(thursday);
  }

  @Test
  void getCurrentNav_usesDateBoundedQuery() {
    var provider = provider("2025-01-15T17:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);
    var fundValue =
        new FundValue(ISIN, yesterday, new BigDecimal("9.69940"), "TULEVA", Instant.now());
    when(fundValueRepository.getLatestValue(ISIN, yesterday)).thenReturn(Optional.of(fundValue));

    assertThat(provider.getDisplayNav(TKF100)).isEqualByComparingTo("9.6994");
  }

  @Test
  void getCurrentNav_throwsWhenNoNavFound() {
    var provider = provider("2025-01-15T17:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);
    when(fundValueRepository.getLatestValue(ISIN, yesterday)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> provider.getDisplayNav(TKF100))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getCurrentNavForIssuing_returnsNavWhenDateMatches() {
    var provider = provider("2025-01-15T17:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    LocalDate dayBefore = LocalDate.of(2025, 1, 13);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);
    when(publicHolidays.previousWorkingDay(yesterday)).thenReturn(dayBefore);
    var fundValue =
        new FundValue(ISIN, yesterday, new BigDecimal("9.6994"), "TULEVA", Instant.now());
    var previousValue =
        new FundValue(ISIN, dayBefore, new BigDecimal("9.6500"), "TULEVA", Instant.now());
    when(fundValueRepository.findLastValueForFund(ISIN)).thenReturn(Optional.of(fundValue));
    when(fundValueRepository.getLatestValue(ISIN, dayBefore))
        .thenReturn(Optional.of(previousValue));

    assertThat(provider.getVerifiedNavForIssuingAndRedeeming(TKF100))
        .isEqualByComparingTo("9.6994");
  }

  @Test
  void getCurrentNavForIssuing_throwsWhenDateDoesNotMatch() {
    var provider = provider("2025-01-15T17:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);
    var staleValue =
        new FundValue(
            ISIN, LocalDate.of(2025, 1, 10), new BigDecimal("9.6994"), "TULEVA", Instant.now());
    when(fundValueRepository.findLastValueForFund(ISIN)).thenReturn(Optional.of(staleValue));

    assertThatThrownBy(() -> provider.getVerifiedNavForIssuingAndRedeeming(TKF100))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getCurrentNavForIssuing_throwsWhenNavDeviatesMoreThan20Percent() {
    var provider = provider("2025-01-15T17:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    LocalDate dayBefore = LocalDate.of(2025, 1, 13);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);
    when(publicHolidays.previousWorkingDay(yesterday)).thenReturn(dayBefore);
    var currentNav =
        new FundValue(ISIN, yesterday, new BigDecimal("500.0000"), "TULEVA", Instant.now());
    var previousNav =
        new FundValue(ISIN, dayBefore, new BigDecimal("10.0000"), "TULEVA", Instant.now());
    when(fundValueRepository.findLastValueForFund(ISIN)).thenReturn(Optional.of(currentNav));
    when(fundValueRepository.getLatestValue(ISIN, dayBefore)).thenReturn(Optional.of(previousNav));

    assertThatThrownBy(() -> provider.getVerifiedNavForIssuingAndRedeeming(TKF100))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getCurrentNavForIssuing_allowsNavChangeWithin20Percent() {
    var provider = provider("2025-01-15T17:00:00");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    LocalDate dayBefore = LocalDate.of(2025, 1, 13);
    when(publicHolidays.previousWorkingDay(today)).thenReturn(yesterday);
    when(publicHolidays.previousWorkingDay(yesterday)).thenReturn(dayBefore);
    var currentNav =
        new FundValue(ISIN, yesterday, new BigDecimal("10.5000"), "TULEVA", Instant.now());
    var previousNav =
        new FundValue(ISIN, dayBefore, new BigDecimal("10.0000"), "TULEVA", Instant.now());
    when(fundValueRepository.findLastValueForFund(ISIN)).thenReturn(Optional.of(currentNav));
    when(fundValueRepository.getLatestValue(ISIN, dayBefore)).thenReturn(Optional.of(previousNav));

    assertThat(provider.getVerifiedNavForIssuingAndRedeeming(TKF100))
        .isEqualByComparingTo("10.5000");
  }
}
