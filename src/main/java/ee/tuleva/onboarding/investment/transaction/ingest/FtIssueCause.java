package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
enum FtIssueCause {
  NO_ORDER(
      "orphanReason",
      (value, confirmation, details) -> "no FT order in the registry for this trade",
      "import the batch's orders into the registry"),
  ORDER_WITHDRAWN(
      "withdrawnOrderOnly",
      (value, confirmation, details) ->
          "FT confirmed a trade whose registry order is cancelled or discarded",
      "check the trade with FT; if it happened, its registry order should not be cancelled"),
  SEVERAL_ORDERS(
      "ambiguousOrderCount",
      (value, confirmation, details) -> value + " registry orders match",
      "find which registry order the trade belongs to; cancel only a duplicate"),
  ORDER_QUANTITY_MISSING(
      "orderQuantityMissing",
      (value, confirmation, details) -> "the order has no quantity",
      "fill in the order's quantity in the registry"),
  ORDER_QUANTITY_DIFFERS(
      "orderQuantityMismatch",
      (value, confirmation, details) -> "order quantity " + value,
      "correct the order in the registry, or have FT correct the confirmation"),
  EXECUTION_MISSING_AFTER_SEB_REPORT(
      "sebReportAsOf",
      (value, confirmation, details) ->
          "no execution in the registry after SEB pending report as of " + value,
      "check how SEB's pending report lists these trades; a row without SEB's Our ref is the"
          + " usual reason"),
  SEB_REPORT_NOT_RECONCILED(
      "sebReportMissingAsOf",
      (value, confirmation, details) ->
          "no SEB pending report as of " + value + " or later reconciled",
      "check that SEB's pending reports arrive and are imported"),
  EXECUTED_QUANTITY_DIFFERS(
      "executedQuantityMismatch",
      (value, confirmation, details) -> "executed quantity " + value,
      "compare FT's confirmation with SEB's pending report"),
  PRICE_DIFFERS(
      "priceDeltaPercent",
      (value, confirmation, details) ->
          "price "
              + confirmation.grossPrice().toPlainString()
              + " vs reference "
              + details.get("referencePrice")
              + " ("
              + new BigDecimal(value).setScale(2, HALF_UP).toPlainString()
              + "%)",
      "check the price with FT"),
  REFERENCE_PRICE_INVALID(
      "invalidReferencePrice",
      (value, confirmation, details) ->
          "reference price " + details.get("referencePrice") + " is not positive",
      "check the instrument's price source");

  final String nextStep;
  private final String detailKey;
  private final IssueText text;

  FtIssueCause(String detailKey, IssueText text, String nextStep) {
    this.detailKey = detailKey;
    this.text = text;
    this.nextStep = nextStep;
  }

  static List<FtIssueCause> of(Map<String, String> details) {
    return Arrays.stream(values()).filter(cause -> details.containsKey(cause.detailKey)).toList();
  }

  String describe(FtConfirmation confirmation, Map<String, String> details) {
    return text.of(Objects.requireNonNull(details.get(detailKey)), confirmation, details);
  }

  @FunctionalInterface
  private interface IssueText {
    String of(String value, FtConfirmation confirmation, Map<String, String> details);
  }
}
