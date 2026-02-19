package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.calculation.PriceSource.EODHD;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
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
class PositionCalculationServiceTest {

  private static final TulevaFund FUND = TUK75;
  private static final LocalDate DATE = LocalDate.of(2025, 1, 10);
  private static final String ISIN = "IE00BFNM3G45";

  @Mock private FundPositionRepository fundPositionRepository;

  @Mock private PositionPriceResolver priceResolver;

  @InjectMocks private PositionCalculationService service;

  @Test
  void calculate_withSecurityPositions_returnsCalculations() {
    BigDecimal quantity = new BigDecimal("1000");
    BigDecimal price = new BigDecimal("100.00");
    FundPosition position = createSecurityPosition(ISIN, quantity);

    ResolvedPrice resolvedPrice =
        ResolvedPrice.builder()
            .eodhdPrice(price)
            .yahooPrice(price)
            .usedPrice(price)
            .priceSource(EODHD)
            .validationStatus(OK)
            .discrepancyPercent(BigDecimal.ZERO)
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, FUND, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(eq(ISIN), eq(DATE), any())).thenReturn(Optional.of(resolvedPrice));

    List<PositionCalculation> result = service.calculate(FUND, DATE);

    assertThat(result).hasSize(1);
    PositionCalculation calculation = result.getFirst();
    assertThat(calculation.isin()).isEqualTo(ISIN);
    assertThat(calculation.fund()).isEqualTo(FUND);
    assertThat(calculation.quantity()).isEqualTo(quantity);
    assertThat(calculation.usedPrice()).isEqualTo(price);
    assertThat(calculation.calculatedMarketValue())
        .isEqualByComparingTo(new BigDecimal("100000.00"));
    assertThat(calculation.validationStatus()).isEqualTo(OK);
  }

  @Test
  void calculate_withPositionWithoutIsin_skipsPosition() {
    FundPosition positionWithoutIsin = createSecurityPosition(null, new BigDecimal("1000"));

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, FUND, SECURITY))
        .thenReturn(List.of(positionWithoutIsin));

    List<PositionCalculation> result = service.calculate(FUND, DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void calculate_withUnknownIsin_skipsPosition() {
    String unknownIsin = "UNKNOWN_ISIN";
    FundPosition position = createSecurityPosition(unknownIsin, new BigDecimal("1000"));

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, FUND, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(eq(unknownIsin), eq(DATE), any())).thenReturn(Optional.empty());

    List<PositionCalculation> result = service.calculate(FUND, DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void calculate_withMultipleFunds_returnsAllCalculations() {
    BigDecimal quantity = new BigDecimal("1000");
    BigDecimal price = new BigDecimal("100.00");
    FundPosition position1 = createSecurityPosition(ISIN, quantity);
    FundPosition position2 = createSecurityPosition(ISIN, quantity);
    position2.setFund(TUK00);

    ResolvedPrice resolvedPrice =
        ResolvedPrice.builder()
            .eodhdPrice(price)
            .usedPrice(price)
            .priceSource(EODHD)
            .validationStatus(OK)
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(eq(DATE), any(), eq(SECURITY)))
        .thenReturn(List.of(position1));
    when(priceResolver.resolve(eq(ISIN), eq(DATE), any())).thenReturn(Optional.of(resolvedPrice));

    List<PositionCalculation> result = service.calculate(List.of(TUK75, TUK00), DATE);

    assertThat(result).hasSize(2);
  }

  @Test
  void calculate_withNullUsedPrice_returnsNullMarketValue() {
    BigDecimal quantity = new BigDecimal("1000");
    FundPosition position = createSecurityPosition(ISIN, quantity);

    ResolvedPrice resolvedPrice =
        ResolvedPrice.builder()
            .eodhdPrice(null)
            .yahooPrice(null)
            .usedPrice(null)
            .priceSource(null)
            .validationStatus(NO_PRICE_DATA)
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, FUND, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(eq(ISIN), eq(DATE), any())).thenReturn(Optional.of(resolvedPrice));

    List<PositionCalculation> result = service.calculate(FUND, DATE);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().calculatedMarketValue()).isNull();
  }

  @Test
  void calculate_withNullQuantity_returnsNullMarketValue() {
    FundPosition position = createSecurityPosition(ISIN, null);
    BigDecimal price = new BigDecimal("100.00");

    ResolvedPrice resolvedPrice =
        ResolvedPrice.builder()
            .eodhdPrice(price)
            .usedPrice(price)
            .priceSource(EODHD)
            .validationStatus(OK)
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, FUND, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(eq(ISIN), eq(DATE), any())).thenReturn(Optional.of(resolvedPrice));

    List<PositionCalculation> result = service.calculate(FUND, DATE);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().calculatedMarketValue()).isNull();
  }

  @Test
  void calculateForLatestDate_withExistingPositions_returnsCalculations() {
    BigDecimal quantity = new BigDecimal("1000");
    BigDecimal price = new BigDecimal("100.00");
    FundPosition position = createSecurityPosition(ISIN, quantity);

    ResolvedPrice resolvedPrice =
        ResolvedPrice.builder()
            .eodhdPrice(price)
            .usedPrice(price)
            .priceSource(EODHD)
            .validationStatus(OK)
            .build();

    when(fundPositionRepository.findLatestNavDateByFund(FUND)).thenReturn(Optional.of(DATE));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, FUND, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(eq(ISIN), eq(DATE), any())).thenReturn(Optional.of(resolvedPrice));

    List<PositionCalculation> result = service.calculateForLatestDate(FUND);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().fund()).isEqualTo(FUND);
    assertThat(result.getFirst().date()).isEqualTo(DATE);
  }

  @Test
  void calculateForLatestDate_withNoPositions_returnsEmptyList() {
    when(fundPositionRepository.findLatestNavDateByFund(FUND)).thenReturn(Optional.empty());

    List<PositionCalculation> result = service.calculateForLatestDate(FUND);

    assertThat(result).isEmpty();
  }

  @Test
  void calculateForLatestDate_withMultipleFunds_returnsCombinedCalculations() {
    BigDecimal quantity = new BigDecimal("1000");
    BigDecimal price = new BigDecimal("100.00");
    LocalDate date1 = LocalDate.of(2025, 1, 10);
    LocalDate date2 = LocalDate.of(2025, 1, 9);

    FundPosition position1 = createSecurityPosition(ISIN, quantity);
    FundPosition position2 = createSecurityPosition(ISIN, quantity);

    ResolvedPrice resolvedPrice =
        ResolvedPrice.builder()
            .eodhdPrice(price)
            .usedPrice(price)
            .priceSource(EODHD)
            .validationStatus(OK)
            .build();

    when(fundPositionRepository.findLatestNavDateByFund(TUK75)).thenReturn(Optional.of(date1));
    when(fundPositionRepository.findLatestNavDateByFund(TUK00)).thenReturn(Optional.of(date2));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(date1, TUK75, SECURITY))
        .thenReturn(List.of(position1));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(date2, TUK00, SECURITY))
        .thenReturn(List.of(position2));
    when(priceResolver.resolve(eq(ISIN), any(), any())).thenReturn(Optional.of(resolvedPrice));

    List<PositionCalculation> result = service.calculateForLatestDate(List.of(TUK75, TUK00));

    assertThat(result).hasSize(2);
  }

  private FundPosition createSecurityPosition(String isin, BigDecimal quantity) {
    return FundPosition.builder()
        .fund(FUND)
        .navDate(DATE)
        .accountType(SECURITY)
        .accountId(isin)
        .accountName("Test Security")
        .quantity(quantity)
        .build();
  }
}
