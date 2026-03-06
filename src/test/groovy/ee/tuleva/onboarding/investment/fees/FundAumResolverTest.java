package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundAumResolverTest {

  @Mock private PositionCalculationRepository positionCalculationRepository;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private PublicHolidays publicHolidays;

  @InjectMocks private FundAumResolver resolver;

  @Test
  void resolveReferenceDate_returnsDate_whenPositionDataMatchesCalendarDate() {
    LocalDate date = LocalDate.of(2025, 7, 15);

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, date))
        .thenReturn(Optional.of(date));
    when(publicHolidays.isWorkingDay(date)).thenReturn(true);

    assertThat(resolver.resolveReferenceDate(TKF100, date)).isEqualTo(date);
  }

  @Test
  void resolveReferenceDate_returnsNull_whenNoNavDate() {
    LocalDate date = LocalDate.of(2025, 7, 15);

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, date))
        .thenReturn(Optional.empty());

    assertThat(resolver.resolveReferenceDate(TKF100, date)).isNull();
  }

  @Test
  void resolveReferenceDate_throwsWhenPositionDataIsStaleOnWorkingDay() {
    LocalDate wednesday = LocalDate.of(2026, 3, 4);
    LocalDate tuesday = LocalDate.of(2026, 3, 3);

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, wednesday))
        .thenReturn(Optional.of(tuesday));
    when(publicHolidays.isWorkingDay(wednesday)).thenReturn(true);

    assertThatThrownBy(() -> resolver.resolveReferenceDate(TUK75, wednesday))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void resolveReferenceDate_allowsFallbackOnWeekend() {
    LocalDate saturday = LocalDate.of(2026, 3, 7);
    LocalDate friday = LocalDate.of(2026, 3, 6);

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, saturday))
        .thenReturn(Optional.of(friday));
    when(publicHolidays.isWorkingDay(saturday)).thenReturn(false);

    assertThat(resolver.resolveReferenceDate(TUK75, saturday)).isEqualTo(friday);
  }

  @Test
  void resolveBaseValue_returnsSumOfMarketValues_forNavFund() {
    LocalDate date = LocalDate.of(2025, 7, 14);
    BigDecimal navValue = new BigDecimal("750000000");

    when(fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            TKF100, date, List.of(CASH, SECURITY, RECEIVABLES, LIABILITY)))
        .thenReturn(navValue);

    assertThat(resolver.resolveBaseValue(TKF100, date)).isEqualTo(navValue);
  }

  @Test
  void resolveBaseValue_returnsSumOfMarketValues_forPensionFund() {
    LocalDate date = LocalDate.of(2025, 7, 14);
    BigDecimal navValue = new BigDecimal("500000000");

    when(fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            TUK75, date, List.of(CASH, SECURITY, RECEIVABLES, LIABILITY)))
        .thenReturn(navValue);

    assertThat(resolver.resolveBaseValue(TUK75, date)).isEqualTo(navValue);
  }

  @Test
  void resolveBaseValue_returnsNull_whenNoPositionData() {
    LocalDate date = LocalDate.of(2025, 7, 15);

    when(fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            TUK75, date, List.of(CASH, SECURITY, RECEIVABLES, LIABILITY)))
        .thenReturn(null);

    assertThat(resolver.resolveBaseValue(TUK75, date)).isNull();
  }
}
