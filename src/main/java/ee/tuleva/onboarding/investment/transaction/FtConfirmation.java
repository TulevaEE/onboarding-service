package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.NORMAL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @Nullable String account,
    boolean suppressed) {

  public FtConfirmation {
    if (type == null) {
      type = NORMAL;
    }
  }

  @JsonCreator
  public FtConfirmation(
      @JsonProperty("fund") TulevaFund fund,
      @JsonProperty("isin") String isin,
      @JsonProperty("tradeDate") LocalDate tradeDate,
      @JsonProperty("quantity") BigDecimal quantity,
      @JsonProperty("grossPrice") BigDecimal grossPrice,
      @JsonProperty("type") @Nullable FtConfirmationType type,
      @JsonProperty("account") @Nullable String account,
      @JsonProperty("suppressed") @Nullable Boolean suppressed) {
    this(
        fund,
        isin,
        tradeDate,
        quantity,
        grossPrice,
        type,
        account,
        Boolean.TRUE.equals(suppressed));
  }

  public FtConfirmation(
      TulevaFund fund,
      String isin,
      LocalDate tradeDate,
      BigDecimal quantity,
      BigDecimal grossPrice) {
    this(fund, isin, tradeDate, quantity, grossPrice, NORMAL, null, false);
  }

  public FtConfirmation(
      TulevaFund fund,
      String isin,
      LocalDate tradeDate,
      BigDecimal quantity,
      BigDecimal grossPrice,
      FtConfirmationType type,
      @Nullable String account) {
    this(fund, isin, tradeDate, quantity, grossPrice, type, account, false);
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
