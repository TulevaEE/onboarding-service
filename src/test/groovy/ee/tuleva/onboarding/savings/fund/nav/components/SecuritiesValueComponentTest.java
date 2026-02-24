package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.calculation.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecuritiesValueComponentTest {

  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private PositionPriceResolver positionPriceResolver;

  @InjectMocks private SecuritiesValueComponent component;

  private static final Instant CUTOFF = Instant.parse("2026-02-01T14:00:00Z");

  @Test
  void calculate_multipliesUnitsByPricesAtCutoff() {
    LocalDate priceDate = LocalDate.of(2026, 2, 1);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(priceDate)
            .positionReportDate(LocalDate.of(2026, 1, 31))
            .priceDate(priceDate)
            .cutoff(CUTOFF)
            .build();

    when(navLedgerRepository.getSecuritiesUnitBalancesAt(CUTOFF))
        .thenReturn(
            Map.of(
                "IE00BFG1TM61", new BigDecimal("1000.00000"),
                "IE00BMDBMY19", new BigDecimal("500.00000")));

    when(positionPriceResolver.resolve("IE00BFG1TM61", priceDate))
        .thenReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("33.875"))
                    .validationStatus(OK)
                    .priceDate(priceDate)
                    .build()));
    when(positionPriceResolver.resolve("IE00BMDBMY19", priceDate))
        .thenReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("43.380"))
                    .validationStatus(OK)
                    .priceDate(priceDate)
                    .build()));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("55565.000");
  }

  @Test
  void calculate_returnsZeroWhenNoUnitBalances() {
    LocalDate priceDate = LocalDate.of(2026, 2, 1);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(priceDate)
            .positionReportDate(LocalDate.of(2026, 1, 31))
            .priceDate(priceDate)
            .cutoff(CUTOFF)
            .build();

    when(navLedgerRepository.getSecuritiesUnitBalancesAt(CUTOFF)).thenReturn(Map.of());

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_skipsIsinsWithNoPriceData() {
    LocalDate priceDate = LocalDate.of(2026, 2, 1);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(priceDate)
            .positionReportDate(LocalDate.of(2026, 1, 31))
            .priceDate(priceDate)
            .cutoff(CUTOFF)
            .build();

    when(navLedgerRepository.getSecuritiesUnitBalancesAt(CUTOFF))
        .thenReturn(Map.of("IE00BFG1TM61", new BigDecimal("1000.00000")));

    when(positionPriceResolver.resolve("IE00BFG1TM61", priceDate)).thenReturn(Optional.empty());

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void getName_returnsSecurities() {
    assertThat(component.getName()).isEqualTo("securities");
  }

  @Test
  void getType_returnsAsset() {
    assertThat(component.getType()).isEqualTo(ASSET);
  }
}
