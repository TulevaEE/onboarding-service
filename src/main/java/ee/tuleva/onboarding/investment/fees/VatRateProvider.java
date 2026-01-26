package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class VatRateProvider {

  private static final LocalDate VAT_INCREASE_DATE = LocalDate.of(2025, 6, 1);
  private static final BigDecimal VAT_RATE_22 = new BigDecimal("0.22");
  private static final BigDecimal VAT_RATE_24 = new BigDecimal("0.24");

  public BigDecimal getVatRate(LocalDate feeMonth) {
    return feeMonth.isBefore(VAT_INCREASE_DATE) ? VAT_RATE_22 : VAT_RATE_24;
  }
}
