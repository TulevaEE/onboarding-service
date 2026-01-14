package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.calculation.PriceSource.EODHD;
import static ee.tuleva.onboarding.investment.calculation.TulevaFund.TUK00;
import static ee.tuleva.onboarding.investment.calculation.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
  private static final String FUND_CODE = FUND.getCode();
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

    when(fundPositionRepository.findByReportingDateAndFundCodeAndAccountType(
            DATE, FUND_CODE, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(ISIN, DATE)).thenReturn(Optional.of(resolvedPrice));

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

    when(fundPositionRepository.findByReportingDateAndFundCodeAndAccountType(
            DATE, FUND_CODE, SECURITY))
        .thenReturn(List.of(positionWithoutIsin));

    List<PositionCalculation> result = service.calculate(FUND, DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void calculate_withUnknownIsin_skipsPosition() {
    String unknownIsin = "UNKNOWN_ISIN";
    FundPosition position = createSecurityPosition(unknownIsin, new BigDecimal("1000"));

    when(fundPositionRepository.findByReportingDateAndFundCodeAndAccountType(
            DATE, FUND_CODE, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(unknownIsin, DATE)).thenReturn(Optional.empty());

    List<PositionCalculation> result = service.calculate(FUND, DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void calculate_withMultipleFunds_returnsAllCalculations() {
    BigDecimal quantity = new BigDecimal("1000");
    BigDecimal price = new BigDecimal("100.00");
    FundPosition position1 = createSecurityPosition(ISIN, quantity);
    FundPosition position2 = createSecurityPosition(ISIN, quantity);
    position2.setFundCode(TUK00.getCode());

    ResolvedPrice resolvedPrice =
        ResolvedPrice.builder()
            .eodhdPrice(price)
            .usedPrice(price)
            .priceSource(EODHD)
            .validationStatus(OK)
            .build();

    when(fundPositionRepository.findByReportingDateAndFundCodeAndAccountType(
            eq(DATE), any(), eq(SECURITY)))
        .thenReturn(List.of(position1));
    when(priceResolver.resolve(ISIN, DATE)).thenReturn(Optional.of(resolvedPrice));

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

    when(fundPositionRepository.findByReportingDateAndFundCodeAndAccountType(
            DATE, FUND_CODE, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(ISIN, DATE)).thenReturn(Optional.of(resolvedPrice));

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

    when(fundPositionRepository.findByReportingDateAndFundCodeAndAccountType(
            DATE, FUND_CODE, SECURITY))
        .thenReturn(List.of(position));
    when(priceResolver.resolve(ISIN, DATE)).thenReturn(Optional.of(resolvedPrice));

    List<PositionCalculation> result = service.calculate(FUND, DATE);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().calculatedMarketValue()).isNull();
  }

  private FundPosition createSecurityPosition(String isin, BigDecimal quantity) {
    return FundPosition.builder()
        .fundCode(FUND_CODE)
        .reportingDate(DATE)
        .accountType(SECURITY)
        .accountId(isin)
        .accountName("Test Security")
        .quantity(quantity)
        .build();
  }
}
