package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.NORMAL;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record FtConfirmation(
    @NotNull TulevaFund fund,
    @NotBlank String isin,
    @NotNull LocalDate tradeDate,
    @NotNull BigDecimal quantity,
    @NotNull BigDecimal grossPrice,
    FtConfirmationType type,
    @Nullable String account) {

  public FtConfirmation {
    if (type == null) {
      type = NORMAL;
    }
  }

  public FtConfirmation(
      TulevaFund fund,
      String isin,
      LocalDate tradeDate,
      BigDecimal quantity,
      BigDecimal grossPrice) {
    this(fund, isin, tradeDate, quantity, grossPrice, NORMAL, null);
  }

  public boolean isCancellation() {
    return type == CANCELLATION;
  }

  public String cancellationSignature() {
    return String.join(
        "|",
        isin,
        account == null ? "" : account,
        tradeDate.toString(),
        grossPrice.toPlainString());
  }
}
