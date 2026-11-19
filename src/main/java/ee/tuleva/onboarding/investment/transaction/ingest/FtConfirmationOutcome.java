package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import java.util.LinkedHashMap;
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
    if (result.quantityStatus() == UNCHECKED) {
      return String.format(
          "FT confirmation could not be checked: fund=%s, isin=%s, tradeDate=%s, quantity=%s,"
              + " error=%s",
          confirmation.fund().name(),
          confirmation.isin(),
          confirmation.tradeDate(),
          confirmation.quantity().toPlainString(),
          result.details().get("verificationError"));
    }
    if (result.isOnlyThePriceMissing()) {
      return String.format(
          "FT price could not be checked: fund=%s, isin=%s, tradeDate=%s, quantity=%s, price=%s"
              + " — no closing price for the trade date by the end of %s; check the price against"
              + " the official close by hand",
          confirmation.fund().name(),
          confirmation.isin(),
          confirmation.tradeDate(),
          confirmation.quantity().toPlainString(),
          confirmation.grossPrice().toPlainString(),
          result.details().get("closingPriceDueBy"));
    }
    if (confirmation.isCancellation()) {
      return String.format(
          "FT cancelled a trade and sent no new confirmation of the same quantity: fund=%s,"
              + " isin=%s, tradeDate=%s,"
              + " quantity=%s — check with FT; if the trade did not happen, cancel the order in"
              + " the registry",
          confirmation.fund().name(),
          confirmation.isin(),
          confirmation.tradeDate(),
          confirmation.quantity().toPlainString());
    }
    return String.format(
        "FT issue: fund=%s, isin=%s, tradeDate=%s, quantity=%s, quantityStatus=%s, priceStatus=%s",
        confirmation.fund().name(),
        confirmation.isin(),
        confirmation.tradeDate(),
        confirmation.quantity().toPlainString(),
        result.quantityStatus(),
        result.priceStatus());
  }
}
