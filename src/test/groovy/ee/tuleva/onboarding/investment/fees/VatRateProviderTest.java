package ee.tuleva.onboarding.investment.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class VatRateProviderTest {

  private final VatRateProvider vatRateProvider = new VatRateProvider();

  @Test
  void getVatRate_returns22PercentBeforeJune2025() {
    LocalDate may2025 = LocalDate.of(2025, 5, 1);

    BigDecimal result = vatRateProvider.getVatRate(may2025);

    assertThat(result).isEqualByComparingTo(new BigDecimal("0.22"));
  }

  @Test
  void getVatRate_returns24PercentFromJune2025() {
    LocalDate june2025 = LocalDate.of(2025, 6, 1);

    BigDecimal result = vatRateProvider.getVatRate(june2025);

    assertThat(result).isEqualByComparingTo(new BigDecimal("0.24"));
  }

  @Test
  void getVatRate_returns24PercentAfterJune2025() {
    LocalDate dec2025 = LocalDate.of(2025, 12, 1);

    BigDecimal result = vatRateProvider.getVatRate(dec2025);

    assertThat(result).isEqualByComparingTo(new BigDecimal("0.24"));
  }
}
