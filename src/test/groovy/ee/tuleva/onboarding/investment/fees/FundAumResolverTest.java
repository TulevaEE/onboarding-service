package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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

  @InjectMocks private FundAumResolver resolver;

  @Test
  void resolveReferenceDate_returnsNavDate_forNavFund() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate navDate = LocalDate.of(2025, 7, 14);

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, date))
        .thenReturn(Optional.of(navDate));

    assertThat(resolver.resolveReferenceDate(TKF100, date)).isEqualTo(navDate);
  }

  @Test
  void resolveReferenceDate_returnsNull_whenNoNavDate() {
    LocalDate date = LocalDate.of(2025, 7, 15);

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, date))
        .thenReturn(Optional.empty());

    assertThat(resolver.resolveReferenceDate(TKF100, date)).isNull();
  }

  @Test
  void resolveReferenceDate_returnsPositionDate_forNonNavFund() {
    LocalDate date = LocalDate.of(2025, 7, 15);

    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date))
        .thenReturn(Optional.of(date));

    assertThat(resolver.resolveReferenceDate(TUK75, date)).isEqualTo(date);
  }

  @Test
  void resolveReferenceDate_returnsNull_whenNoPositionData() {
    LocalDate date = LocalDate.of(2025, 7, 15);

    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date)).thenReturn(Optional.empty());

    assertThat(resolver.resolveReferenceDate(TUK75, date)).isNull();
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
  void resolveBaseValue_returnsTotalMarketValue_forNonNavFund() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    BigDecimal positionValue = new BigDecimal("500000000");

    when(positionCalculationRepository.getTotalMarketValue(TUK75, date))
        .thenReturn(Optional.of(positionValue));

    assertThat(resolver.resolveBaseValue(TUK75, date)).isEqualTo(positionValue);
  }

  @Test
  void resolveBaseValue_returnsZero_whenNoPositionData() {
    LocalDate date = LocalDate.of(2025, 7, 15);

    when(positionCalculationRepository.getTotalMarketValue(TUK75, date))
        .thenReturn(Optional.empty());

    assertThat(resolver.resolveBaseValue(TUK75, date)).isEqualByComparingTo(ZERO);
  }
}
