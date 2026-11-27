package ee.tuleva.onboarding.investment.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PriceDeviationConfirmation(
    @NotNull @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{9}[0-9]") String isin,
    @NotNull @Positive @Digits(integer = 12, fraction = 8) BigDecimal price) {

  boolean confirms(PriceDeviation deviation) {
    return isin.equals(deviation.isin()) && price.compareTo(deviation.manualPrice()) == 0;
  }
}
