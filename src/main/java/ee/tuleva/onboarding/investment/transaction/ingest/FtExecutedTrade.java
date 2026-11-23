package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PRICE_MISSING;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record FtExecutedTrade(
    TulevaFund fund,
    String isin,
    LocalDate tradeDate,
    UUID orderUuid,
    BigDecimal executedQuantity,
    @Nullable BigDecimal sebPrice,
    FtConfirmationResult result)
    implements FtCheckOutcome {

  private static final int DEVIATION_DECIMALS = 2;
  private static final String ASK_FT_FOR_THE_MISSING_CONFIRMATION =
      "ask FT for the missing confirmation";
  private static final FtConfirmationResult CONFIRMED_BY_FT =
      new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of());

  static FtExecutedTrade confirmedByFt(TransactionOrder order, SebExecutedTotal executed) {
    return of(order, executed, CONFIRMED_BY_FT);
  }

  static FtExecutedTrade of(
      TransactionOrder order, SebExecutedTotal executed, FtConfirmationResult result) {
    return new FtExecutedTrade(
        order.getFund(),
        order.getInstrumentIsin(),
        FtOrderSelection.tradeDate(order),
        order.getOrderUuid(),
        executed.quantity(),
        executed.averagePrice(),
        result);
  }

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
    if (sebPrice != null) {
      facts.put("sebPrice", sebPrice.toPlainString());
    }
    return facts;
  }

  @Override
  public String describe() {
    return String.format(
        "SEB executed an FT trade with no readable FT confirmation by the end of %s: fund=%s,"
            + " isin=%s, tradeDate=%s, quantity=%s, sebPrice=%s — %s",
        detail("confirmationDueBy"),
        fund.name(),
        isin,
        tradeDate,
        executedQuantity.toPlainString(),
        sebPrice == null ? "none" : sebPrice.toPlainString(),
        priceResult());
  }

  @Override
  public List<String> nextSteps() {
    if (result.priceStatus() == PRICE_MISSING) {
      return List.of(
          ASK_FT_FOR_THE_MISSING_CONFIRMATION, CHECK_THE_PRICE_AGAINST_THE_OFFICIAL_CLOSE_BY_HAND);
    }
    return List.of(ASK_FT_FOR_THE_MISSING_CONFIRMATION);
  }

  private String priceResult() {
    return switch (result.priceStatus()) {
      case OK -> "price within tolerance of the closing price " + detail("referencePrice");
      case PRICE_MISSING ->
          "no closing price for the trade date by the end of " + detail("closingPriceDueBy");
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
