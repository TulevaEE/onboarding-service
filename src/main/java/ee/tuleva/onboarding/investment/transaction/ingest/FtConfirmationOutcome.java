package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record FtConfirmationOutcome(FtConfirmation confirmation, FtConfirmationResult result)
    implements FtCheckOutcome {

  @Override
  public String auditKey() {
    return FtConfirmationAuditRecorder.dedupKey(confirmation);
  }

  @Override
  public Map<String, Object> auditFacts() {
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("fund", confirmation.fund().name());
    facts.put("isin", confirmation.isin());
    facts.put("tradeDate", confirmation.tradeDate().toString());
    facts.put("quantity", confirmation.quantity().toPlainString());
    facts.put("grossPrice", confirmation.grossPrice().toPlainString());
    facts.put("type", confirmation.type().name());
    if (confirmation.account() != null) {
      facts.put("account", confirmation.account());
    }
    return facts;
  }

  @Override
  public String describe() {
    return switch (line()) {
      case NOT_CHECKED ->
          String.format(
              "FT confirmation could not be checked: fund=%s, isin=%s, tradeDate=%s, quantity=%s,"
                  + " error=%s",
              confirmation.fund().name(),
              confirmation.isin(),
              confirmation.tradeDate(),
              confirmation.quantity().toPlainString(),
              result.details().get("verificationError"));
      case PRICE_NOT_CHECKED ->
          String.format(
              "FT price could not be checked: fund=%s, isin=%s, tradeDate=%s, quantity=%s,"
                  + " price=%s — no closing price for the trade date by the end of %s",
              confirmation.fund().name(),
              confirmation.isin(),
              confirmation.tradeDate(),
              confirmation.quantity().toPlainString(),
              confirmation.grossPrice().toPlainString(),
              result.details().get("closingPriceDueBy"));
      case CANCELLED_WITHOUT_REPLACEMENT ->
          String.format(
              "FT cancelled a trade and sent no new confirmation of the same quantity: fund=%s,"
                  + " isin=%s, tradeDate=%s, quantity=%s",
              confirmation.fund().name(),
              confirmation.isin(),
              confirmation.tradeDate(),
              confirmation.quantity().toPlainString());
      case ISSUE ->
          String.format(
              "FT issue: fund=%s, isin=%s, tradeDate=%s, quantity=%s — %s",
              confirmation.fund().name(),
              confirmation.isin(),
              confirmation.tradeDate(),
              confirmation.quantity().toPlainString(),
              causes().stream()
                  .map(cause -> cause.describe(confirmation, result.details()))
                  .collect(joining("; ")));
      case ISSUE_OF_NO_KNOWN_CAUSE ->
          String.format(
              "FT issue: fund=%s, isin=%s, tradeDate=%s, quantity=%s, quantityStatus=%s,"
                  + " priceStatus=%s",
              confirmation.fund().name(),
              confirmation.isin(),
              confirmation.tradeDate(),
              confirmation.quantity().toPlainString(),
              result.quantityStatus(),
              result.priceStatus());
    };
  }

  @Override
  public List<String> nextSteps() {
    return line() == Line.ISSUE
        ? causes().stream().map(cause -> cause.nextStep).toList()
        : line().nextSteps;
  }

  private List<FtIssueCause> causes() {
    return FtIssueCause.of(result.details());
  }

  private Line line() {
    if (result.quantityStatus() == UNCHECKED) {
      return Line.NOT_CHECKED;
    }
    if (result.isOnlyThePriceMissing()) {
      return Line.PRICE_NOT_CHECKED;
    }
    if (confirmation.isCancellation()) {
      return Line.CANCELLED_WITHOUT_REPLACEMENT;
    }
    return causes().isEmpty() ? Line.ISSUE_OF_NO_KNOWN_CAUSE : Line.ISSUE;
  }

  private enum Line {
    NOT_CHECKED(List.of()),
    PRICE_NOT_CHECKED(List.of(CHECK_THE_PRICE_AGAINST_THE_OFFICIAL_CLOSE_BY_HAND)),
    CANCELLED_WITHOUT_REPLACEMENT(
        List.of(
            "check the cancelled trade with FT; if it did not happen, cancel the order in the"
                + " registry")),
    ISSUE(List.of()),
    ISSUE_OF_NO_KNOWN_CAUSE(List.of());

    private final List<String> nextSteps;

    Line(List<String> nextSteps) {
      this.nextSteps = nextSteps;
    }
  }
}
