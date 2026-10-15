package ee.tuleva.onboarding.banking.processor;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.ledger.FundBankLedger.TradeSettlementDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeSettlementParser {

  private static final int BROKER_REFERENCE = 0;
  private static final int TICKER = 1;
  private static final int UNITS = 2;
  private static final int PRICE = 3;

  private final InstrumentReferenceService instrumentReferenceService;

  public record TradeSettlementInfo(
      String brokerReference,
      String isin,
      String ticker,
      String displayName,
      BigDecimal units,
      BigDecimal price) {

    public BigDecimal signedUnits(BigDecimal cashAmount) {
      var scaled = units.abs().setScale(5, RoundingMode.HALF_UP);
      return cashAmount.signum() > 0 ? scaled.negate() : scaled;
    }

    public TradeSettlementDetails ledgerDetails() {
      return new TradeSettlementDetails(brokerReference, isin, ticker, displayName, price);
    }
  }

  public Optional<TradeSettlementInfo> parse(String remittanceInfo) {
    if (remittanceInfo == null || remittanceInfo.isEmpty()) {
      return Optional.empty();
    }

    String[] segments = remittanceInfo.split("/");
    if (segments.length <= PRICE) {
      return Optional.empty();
    }

    String tickerSegment = segments[TICKER].trim();
    int spaceIndex = tickerSegment.indexOf(' ');
    String ticker = spaceIndex > 0 ? tickerSegment.substring(0, spaceIndex) : tickerSegment;

    Optional<InstrumentReference> instrument =
        instrumentReferenceService
            .findByTicker(ticker)
            .or(() -> instrumentReferenceService.findByBloombergTicker(ticker));

    return instrument.map(
        found ->
            new TradeSettlementInfo(
                segments[BROKER_REFERENCE].trim(),
                found.getIsin(),
                ticker,
                found.getDisplayName(),
                new BigDecimal(segments[UNITS].trim()),
                new BigDecimal(segments[PRICE].trim())));
  }
}
