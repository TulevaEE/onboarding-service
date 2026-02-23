package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.calculation.PriceSource.EODHD;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositionCalculationPersistenceServiceTest {

  private static final String ISIN = "IE00BFNM3G45";
  private static final TulevaFund FUND = TUK75;
  private static final LocalDate DATE = LocalDate.of(2025, 1, 10);
  private static final Instant CREATED_AT = Instant.parse("2025-01-10T12:00:00Z");

  @Mock private PositionCalculationRepository repository;

  @InjectMocks private PositionCalculationPersistenceService service;

  @Test
  void save_withNewCalculation_insertsEntity() {
    PositionCalculation calculation = createCalculation();
    when(repository.findByIsinAndFundAndDate(ISIN, FUND, DATE)).thenReturn(Optional.empty());

    service.save(calculation);

    var expectedEntity =
        InvestmentPositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(DATE)
            .quantity(new BigDecimal("1000"))
            .usedPrice(new BigDecimal("100.00"))
            .priceSource(EODHD)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(OK)
            .priceDate(DATE)
            .createdAt(CREATED_AT)
            .build();
    verify(repository).save(expectedEntity);
  }

  @Test
  void save_withExistingCalculation_updatesEntity() {
    PositionCalculation calculation = createCalculation();
    InvestmentPositionCalculation existingEntity =
        InvestmentPositionCalculation.builder()
            .id(1L)
            .isin(ISIN)
            .fund(FUND)
            .date(DATE)
            .quantity(new BigDecimal("500"))
            .usedPrice(new BigDecimal("50.00"))
            .calculatedMarketValue(new BigDecimal("25000.00"))
            .priceSource(EODHD)
            .validationStatus(OK)
            .build();

    when(repository.findByIsinAndFundAndDate(ISIN, FUND, DATE))
        .thenReturn(Optional.of(existingEntity));

    service.save(calculation);

    verify(repository).save(existingEntity);
    assertThat(existingEntity.getId()).isEqualTo(1L);
    assertThat(existingEntity.getQuantity()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(existingEntity.getUsedPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(existingEntity.getCalculatedMarketValue())
        .isEqualByComparingTo(new BigDecimal("100000.00"));
  }

  @Test
  void saveAll_withMultipleCalculations_savesEach() {
    PositionCalculation calculation1 = createCalculation();
    PositionCalculation calculation2 =
        PositionCalculation.builder()
            .isin("IE00BFNM3D14")
            .fund(FUND)
            .date(DATE)
            .quantity(new BigDecimal("2000"))
            .usedPrice(new BigDecimal("50.00"))
            .priceSource(EODHD)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(OK)
            .priceDate(DATE)
            .createdAt(CREATED_AT)
            .build();

    when(repository.findByIsinAndFundAndDate(any(), any(), any())).thenReturn(Optional.empty());

    service.saveAll(List.of(calculation1, calculation2));

    verify(repository, times(2)).save(any(InvestmentPositionCalculation.class));
  }

  @Test
  void saveAll_withEmptyList_doesNothing() {
    service.saveAll(List.of());

    verify(repository, never()).save(any());
  }

  private PositionCalculation createCalculation() {
    return PositionCalculation.builder()
        .isin(ISIN)
        .fund(FUND)
        .date(DATE)
        .quantity(new BigDecimal("1000"))
        .usedPrice(new BigDecimal("100.00"))
        .priceSource(EODHD)
        .calculatedMarketValue(new BigDecimal("100000.00"))
        .validationStatus(OK)
        .priceDate(DATE)
        .createdAt(CREATED_AT)
        .build();
  }
}
