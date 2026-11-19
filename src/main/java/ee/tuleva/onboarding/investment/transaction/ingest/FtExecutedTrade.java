package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
record FtExecutedTrade(
    TulevaFund fund,
    String isin,
    LocalDate tradeDate,
    UUID orderUuid,
    BigDecimal executedQuantity,
    BigDecimal sebPrice,
    FtConfirmationResult result)
    implements FtCheckOutcome {

  private static final int DEVIATION_DECIMALS = 2;

  @Override
  public String auditKey() {
    return "FT_TRADE|" + orderUuid;
  }

  @Override
  public Map<String, Object> auditFacts() {
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("fund", fund.name());
    facts.put("isin", isin);
    facts.put("tradeDate", tradeDate.toString());
    facts.put("orderUuid", orderUuid.toString());
    facts.put("executedQuantity", executedQuantity.toPlainString());
    facts.put("sebPrice", sebPrice.toPlainString());
    return facts;
  }

  @Override
  public String describe() {
    return String.format(
        "SEB executed an FT trade with no readable FT confirmation by the end of %s: fund=%s,"
            + " isin=%s, tradeDate=%s, quantity=%s, sebPrice=%s — %s; ask FT for the confirmation",
        detail("confirmationDueBy"),
        fund.name(),
        isin,
        tradeDate,
        executedQuantity.toPlainString(),
        sebPrice.toPlainString(),
        priceResult());
  }

  private String priceResult() {
    return switch (result.priceStatus()) {
      case OK -> "price within tolerance of the closing price " + detail("referencePrice");
      case PRICE_MISSING ->
          "no closing price for the trade date by the end of "
              + detail("closingPriceDueBy")
              + ", check the price against the official close by hand";
      case ERROR -> priceError();
      default -> "price could not be checked: " + detail("verificationError");
    };
  }

  private String priceError() {
    if (!result.details().containsKey("priceDeltaPercent")) {
      return "closing price " + detail("referencePrice") + " is not valid";
    }
    BigDecimal deviation =
        new BigDecimal(detail("priceDeltaPercent")).setScale(DEVIATION_DECIMALS, HALF_UP);
    return "price "
        + deviation.toPlainString()
        + "% off the closing price "
        + detail("referencePrice");
  }

  private String detail(String key) {
    return String.valueOf(result.details().get(key));
  }
}
